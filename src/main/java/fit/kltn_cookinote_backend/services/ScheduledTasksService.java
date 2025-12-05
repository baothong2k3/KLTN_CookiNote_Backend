/*
 * @ (#) ScheduledTasksService.java    1.0    04/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 04/12/2025
 * @version: 1.0
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.kltn_cookinote_backend.dtos.NutritionInfo;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.RecipeIngredient;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasksService {

    private final RecipeRepository recipeRepository;
    private final AiRecipeService aiRecipeService;
    // Inject TransactionManager để quản lý transaction thủ công
    private final PlatformTransactionManager transactionManager;
    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;

    // Chạy định kỳ mỗi 5 phút (300,000 ms)
    @Scheduled(fixedDelay = 300000)
    public void autoFillNutritionInfo() {
        log.info("Bắt đầu Job quét các recipe thiếu thông tin dinh dưỡng...");

        // Tạo TransactionTemplate từ Manager
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // 1. Lấy 20 công thức mỗi lần chạy để xử lý nhanh gọn
        Pageable limit = PageRequest.of(0, 20);
        List<Recipe> missingRecipes = recipeRepository.findRecipesMissingNutrition(limit);

        if (missingRecipes.isEmpty()) return;

        for (Recipe detachedRecipe : missingRecipes) {
            try {
                // --- XỬ LÝ TRANSACTION CỤC BỘ ---
                // Code trong execute sẽ chạy trong 1 transaction mới
                transactionTemplate.execute(status -> {
                    processSingleRecipe(detachedRecipe);
                    return null; // Lambda yêu cầu return
                });

                // Nghỉ ngắn giữa các item để tránh spam AI (Nằm ngoài transaction)
                Thread.sleep(2000);
            } catch (Exception e) {
                log.error("Lỗi job ID {}: {}", detachedRecipe.getId(), e.getMessage());
            }
        }
    }

    // Hàm này được gọi bên trong transactionTemplate.execute()
    private void processSingleRecipe(Recipe detachedRecipe) {
        // [QUAN TRỌNG] Tải lại Recipe từ DB để gắn vào Transaction hiện tại
        // Lúc này 'recipe' sẽ ở trạng thái Persistent (Managed), khắc phục lỗi LazyInitializationException
        Recipe recipe = recipeRepository.findById(detachedRecipe.getId()).orElse(null);

        if (recipe == null) {
            return; // Recipe có thể đã bị xóa
        }

        // Gọi AI service - Hibernate sẽ tự động fetch ingredients khi cần
        NutritionInfo info = aiRecipeService.estimateNutrition(recipe);

        if (info != null) {
            boolean updated = false;

            // Chỉ cập nhật nếu trường đó đang null (tránh ghi đè dữ liệu user nhập)
            if (recipe.getCalories() == null) {
                recipe.setCalories(info.calories());
                updated = true;
            }
            if (recipe.getServings() == null) {
                recipe.setServings(info.servings());
                updated = true;
            }

            if (updated) {
                recipeRepository.save(recipe);
                log.info("Đã cập nhật dinh dưỡng cho Recipe ID: {} (Calo: {}, Khẩu phần: {})",
                        recipe.getId(), info.calories(), info.servings());
            }
        }
    }

    // Job chạy định kỳ mỗi 10 phút để vector hóa dữ liệu
    @Scheduled(fixedDelay = 600000)
    public void syncRecipeEmbeddings() {
        log.info("Bắt đầu Job đồng bộ Vector Embedding...");

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // Lấy 10 công thức chưa có vector để xử lý (tránh lấy nhiều gây quá tải)
        Pageable limit = PageRequest.of(0, 10);
        List<Recipe> missingRecipes = recipeRepository.findRecipesMissingEmbedding(limit);

        if (missingRecipes.isEmpty()) {
            return;
        }

        for (Recipe detachedRecipe : missingRecipes) {
            try {
                // Xử lý trong transaction riêng biệt
                transactionTemplate.execute(status -> {
                    processEmbeddingForRecipe(detachedRecipe.getId());
                    return null;
                });

                // Nghỉ 1 giây để tránh Rate Limit của Gemini
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Lỗi job embedding ID {}: {}", detachedRecipe.getId(), e.getMessage());
            }
        }
    }

    private void processEmbeddingForRecipe(Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId).orElse(null);
        if (recipe == null) return;

        // 1. Tạo chuỗi "Semantic String" chứa toàn bộ ý nghĩa của món ăn
        String semanticText = buildSemanticString(recipe);

        // 2. Gọi AI lấy Vector
        List<Double> vector = geminiApiClient.getEmbedding(semanticText);

        if (!vector.isEmpty()) {
            try {
                // 3. Lưu vector vào DB dưới dạng JSON String
                String vectorJson = objectMapper.writeValueAsString(vector);
                recipe.setEmbeddingVector(vectorJson);
                recipeRepository.save(recipe);
                log.info("Đã tạo vector embedding cho Recipe ID: {}", recipe.getId());
            } catch (Exception e) {
                log.error("Lỗi convert vector sang JSON: {}", e.getMessage());
            }
        }
    }

    // Helper tạo chuỗi mô tả ngữ nghĩa cho AI hiểu
    private String buildSemanticString(Recipe r) {
        // 1. Lấy danh sách nguyên liệu KÈM ĐỊNH LƯỢNG
        // Format: "Thịt bò (200g), Bánh phở (500g), Hạt nêm (1 muỗng)"
        String ingredients = r.getIngredients().stream()
                .map(i -> {
                    String qty = (i.getQuantity() != null && !i.getQuantity().isBlank())
                            ? " (" + i.getQuantity() + ")"
                            : "";
                    return i.getName() + qty;
                })
                .collect(Collectors.joining(", "));

        // 2. Xử lý dữ liệu số (tránh null)
        String caloriesInfo = r.getCalories() != null ? r.getCalories() + " kcal" : "chưa rõ calo";
        String servingsInfo = r.getServings() != null ? r.getServings() + " người ăn" : "chưa rõ khẩu phần";

        // 3. Tạo chuỗi ngữ nghĩa đầy đủ
        // Format: "Món ăn: [Tên]. Mô tả: [Desc]. Nguyên liệu: [Tên (Định lượng)]. Dinh dưỡng gốc:..."
        return String.format(
                "Món ăn: %s. Mô tả: %s. Nguyên liệu: %s. Độ khó: %s. Dinh dưỡng gốc: %s cho %s.",
                r.getTitle(),
                (r.getDescription() != null ? r.getDescription() : ""),
                ingredients,
                (r.getDifficulty() != null ? r.getDifficulty().name() : ""),
                caloriesInfo,
                servingsInfo
        );
    }
}