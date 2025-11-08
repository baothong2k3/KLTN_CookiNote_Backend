/*
 * @ (#) IngredientClassificationService.java    1.0    08/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 08/11/2025
 * @version: 1.0
 */

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.kltn_cookinote_backend.enums.IngredientType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service này tải và lưu trữ dữ liệu phân loại nguyên liệu (MAIN/SECONDARY)
 * từ file JSON trong classpath để phục vụ việc chấm điểm gợi ý công thức.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngredientClassificationService {

    private final ObjectMapper objectMapper;
    private final IngredientSynonymService synonymService;

    private Map<Long, Map<String, IngredientType>> classificationData = Collections.emptyMap();

    // Tên file "training"
    private static final String CLASSIFICATION_FILE = "ingredient-classification.json";

    @PostConstruct
    public void loadData() {
        try (InputStream inputStream = new ClassPathResource(CLASSIFICATION_FILE).getInputStream()) {

            // 1. Đọc file JSON thô: Map<String, Map<String, String>>
            TypeReference<Map<String, Map<String, String>>> rawTypeRef = new TypeReference<>() {
            };
            Map<String, Map<String, String>> rawData = objectMapper.readValue(inputStream, rawTypeRef);

            // 2. Chuyển đổi (normalize) dữ liệu để tra cứu
            Map<Long, Map<String, IngredientType>> processedData = new HashMap<>();

            for (Map.Entry<String, Map<String, String>> recipeEntry : rawData.entrySet()) {
                try {
                    Long recipeId = Long.parseLong(recipeEntry.getKey());

                    // Chuẩn hóa key (tên nguyên liệu) về dạng CHUẨN ĐỒNG NGHĨA
                    Map<String, IngredientType> ingredientMap = recipeEntry.getValue().entrySet().stream()
                            .collect(Collectors.toMap(
                                    // CHUẨN HÓA BẰNG TỪ ĐIỂN ĐỒNG NGHĨA
                                    entry -> synonymService.getStandardizedName(entry.getKey()), // <-- THAY ĐỔI Ở ĐÂY
                                    entry -> "MAIN".equalsIgnoreCase(entry.getValue()) ? IngredientType.MAIN : IngredientType.SECONDARY,
                                    (existing, replacement) -> existing // Giữ lại nếu có trùng key sau chuẩn hóa
                            ));

                    processedData.put(recipeId, Collections.unmodifiableMap(ingredientMap));
                } catch (NumberFormatException e) {
                    log.warn("Bỏ qua recipeId không hợp lệ trong file {}: {}", CLASSIFICATION_FILE, recipeEntry.getKey());
                }
            }

            this.classificationData = Collections.unmodifiableMap(processedData);
            log.info("Tải thành công {} phân loại công thức từ {}", this.classificationData.size(), CLASSIFICATION_FILE);

        } catch (Exception e) {
            log.error("Không thể tải file phân loại nguyên liệu: {}. Tính năng gợi ý sẽ không hoạt động chính xác.", CLASSIFICATION_FILE, e);
            this.classificationData = Collections.emptyMap();
        }
    }

    /**
     * Lấy bản đồ phân loại (đã chuẩn hóa key) cho một recipe.
     *
     * @param recipeId ID của công thức
     * @return Map<StandardizedIngredientName, IngredientType>. Trả về map rỗng nếu không tìm thấy.
     */
    public Map<String, IngredientType> getClassificationsForRecipe(Long recipeId) {
        return classificationData.getOrDefault(recipeId, Collections.emptyMap());
    }
}
