/*
 * @ (#) RecipeImportServiceImpl.java    1.0    16/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 16/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.GeneratedRecipeResponse;
import fit.kltn_cookinote_backend.services.RecipeImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeImportServiceImpl implements RecipeImportService {

    private static final int TIMEOUT = 10000; // 10 giây

    // Pattern để thử tách số lượng/đơn vị khỏi tên nguyên liệu
    // Ví dụ: "1 kg thịt bò" -> group 1: "1 kg", group 2: "thịt bò"
    // Ví dụ: "Tỏi, sả" -> không khớp -> group 1: null, group 2: "Tỏi, sả"
    private static final Pattern INGREDIENT_PATTERN = Pattern.compile(
            "^([0-9/.,]+\\s*(?:kg|g|l|ml|củ|quả|nhánh|muỗng|thìa|mcf|mc|gói|hộp|trái|bó|con|cây|tách|lon|miếng|bìa|cốc|lát))[\\s:]+(.*)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );


    @Override
    public GeneratedRecipeResponse importRecipeFromUrl(String url) throws IOException {
        log.info("Bắt đầu cào dữ liệu công thức từ URL: {}", url);

        try {
            Document doc = Jsoup.connect(url)
                    .timeout(TIMEOUT)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

            GeneratedRecipeResponse response = new GeneratedRecipeResponse();

            // Lấy tiêu đề và mô tả (logic này khá ổn)
            response.setTitle(scrapeTitle(doc));
            response.setDescription(scrapeDescription(doc));

            // --- LOGIC MỚI ---
            // Thử logic cào riêng cho VnExpress trước
            if (url.contains("vnexpress.net")) {
                log.debug("Phát hiện URL VnExpress. Sử dụng logic cào riêng.");
                Element articleBody = doc.selectFirst(".fck_detail"); // Container chính của VnExpress
                if (articleBody != null) {
                    response.setIngredients(scrapeVnExpressIngredients(articleBody));
                    response.setSteps(scrapeVnExpressSteps(articleBody));
                }
            }

            // --- FALLBACK (Dự phòng) ---
            // Nếu logic riêng không tìm thấy gì, thử lại logic chung
            if (response.getIngredients() == null || response.getIngredients().isEmpty()) {
                log.debug("Không tìm thấy nguyên liệu từ logic riêng. Thử logic chung.");
                response.setIngredients(scrapeGenericIngredients(doc));
            }
            if (response.getSteps() == null || response.getSteps().isEmpty()) {
                log.debug("Không tìm thấy bước từ logic riêng. Thử logic chung.");
                response.setSteps(scrapeGenericSteps(doc));
            }
            // --- HẾT LOGIC MỚI ---

            log.info("Cào dữ liệu thành công cho: {}", response.getTitle());
            return response;

        } catch (Exception e) {
            log.error("Không thể cào dữ liệu từ URL: {}. Lỗi: {}", url, e.getMessage());
            throw new IOException("Không thể tải hoặc phân tích cú pháp URL. Website có thể đã chặn hoặc cấu trúc không được hỗ trợ.", e);
        }
    }

    /**
     * Cố gắng lấy Tiêu đề (Tên món ăn)
     */
    private String scrapeTitle(Document doc) {
        // Ưu tiên thẻ meta 'og:title'
        Element ogTitle = doc.selectFirst("meta[property=og:title]");
        if (ogTitle != null) {
            return ogTitle.attr("content");
        }
        // Sau đó thử thẻ <h1> đầu tiên
        Element h1 = doc.selectFirst("h1");
        if (h1 != null) {
            return h1.text();
        }
        // Cuối cùng là thẻ <title> của trang
        return doc.title();
    }

    /**
     * Cố gắng lấy Mô tả
     */
    private String scrapeDescription(Document doc) {
        // Ưu tiên thẻ meta 'og:description'
        Element ogDesc = doc.selectFirst("meta[property=og:description]");
        if (ogDesc != null) {
            return ogDesc.attr("content");
        }
        // Thử thẻ meta 'description'
        Element metaDesc = doc.selectFirst("meta[name=description]");
        if (metaDesc != null) {
            return metaDesc.attr("content");
        }
        return null; // Bỏ qua nếu không tìm thấy
    }

    // --- CÁC HÀM CÀO DỮ LIỆU CHUNG (FALLBACK) ---

    /**
     * (Chung) Cố gắng lấy danh sách Nguyên liệu.
     */
    private List<GeneratedRecipeResponse.IngredientDto> scrapeGenericIngredients(Document doc) {
        Elements ingredientElements = doc.select(
                ".recipe-ingredients li, " +
                        ".ingredient-list .item, " +
                        ".ingredients-list li, " +
                        "[class*=ingredient] li"
        );

        if (ingredientElements.isEmpty()) {
            log.warn("[Generic] Không tìm thấy selector nguyên liệu phổ biến.");
            return new ArrayList<>();
        }

        return ingredientElements.stream()
                .map(el -> parseIngredientText(el.text()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * (Chung) Cố gắng lấy danh sách các Bước thực hiện
     */
    private List<GeneratedRecipeResponse.StepDto> scrapeGenericSteps(Document doc) {
        Elements stepElements = doc.select(
                ".recipe-steps li, " +
                        ".instruction-list .item, " +
                        ".directions-list li, " +
                        "[class*=instruction] li, " +
                        "[class*=step] li, " +
                        "[class*=step] p"
        );

        if (stepElements.isEmpty()) {
            log.warn("[Generic] Không tìm thấy selector bước thực hiện phổ biến.");
            return new ArrayList<>();
        }

        List<GeneratedRecipeResponse.StepDto> steps = new ArrayList<>();
        int stepNo = 1;
        for (Element el : stepElements) {
            String text = el.text().trim();
            if (!text.isEmpty()) {
                GeneratedRecipeResponse.StepDto dto = new GeneratedRecipeResponse.StepDto();
                dto.setStepNo(stepNo++);
                dto.setContent(text);
                steps.add(dto);
            }
        }
        return steps;
    }

    // --- CÁC HÀM CÀO DỮ LIỆU DÀNH RIÊNG CHO VNEXPRESS ---

    /**
     * (VnExpress) Lấy nguyên liệu từ container .fck_detail
     */
    private List<GeneratedRecipeResponse.IngredientDto> scrapeVnExpressIngredients(Element articleBody) {
        List<GeneratedRecipeResponse.IngredientDto> ingredients = new ArrayList<>();
        Elements paragraphs = articleBody.select("p");
        boolean foundIngredients = false;

        for (Element p : paragraphs) {
            String pText = p.text().trim();

            if (p.selectFirst("strong:contains(Nguyên liệu)") != null) {
                foundIngredients = true;
                continue; // Bỏ qua dòng tiêu đề "Nguyên liệu"
            }

            // Dừng lại khi gặp tiêu đề "Cách làm"
            if (p.selectFirst("strong:contains(Cách làm)") != null) {
                foundIngredients = false;
                break;
            }

            if (foundIngredients && !pText.isEmpty()) {
                ingredients.add(parseIngredientText(pText));
            }
        }
        log.debug("[VnExpress] Tìm thấy {} nguyên liệu.", ingredients.size());
        return ingredients;
    }

    /**
     * (VnExpress) Lấy các bước thực hiện từ container .fck_detail
     */
    private List<GeneratedRecipeResponse.StepDto> scrapeVnExpressSteps(Element articleBody) {
        List<GeneratedRecipeResponse.StepDto> steps = new ArrayList<>();
        Elements paragraphs = articleBody.select("p");
        boolean foundSteps = false;
        int stepNo = 1;

        for (Element p : paragraphs) {
            String pText = p.text().trim();

            if (p.selectFirst("strong:contains(Cách làm)") != null) {
                foundSteps = true;
                continue; // Bỏ qua dòng tiêu đề "Cách làm"
            }

            // Nếu tìm thấy một thẻ <p> bắt đầu bằng "Bước X:", đó là một bước
            if (foundSteps && pText.toLowerCase().startsWith("bước ")) {
                GeneratedRecipeResponse.StepDto dto = new GeneratedRecipeResponse.StepDto();
                dto.setStepNo(stepNo++);
                // Xóa "Bước X: " ở đầu
                dto.setContent(pText.replaceAll("(?i)^bước\\s[0-9]+[:.]?\\s*", ""));
                steps.add(dto);
            }
        }
        log.debug("[VnExpress] Tìm thấy {} bước thực hiện.", steps.size());
        return steps;
    }

    /**
     * Helper: Tách chuỗi nguyên liệu (ví dụ: "1 kg thịt bò") thành
     * quantity ("1 kg") và name ("thịt bò").
     */
    private GeneratedRecipeResponse.IngredientDto parseIngredientText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        text = text.trim();

        Matcher matcher = INGREDIENT_PATTERN.matcher(text);
        GeneratedRecipeResponse.IngredientDto dto = new GeneratedRecipeResponse.IngredientDto();

        if (matcher.find()) {
            // Khớp regex: Tách thành số lượng và tên
            dto.setQuantity(matcher.group(1).trim());
            dto.setName(matcher.group(2).trim());
        } else {
            // Không khớp regex: Toàn bộ là tên
            dto.setQuantity(null);
            dto.setName(text);
        }
        return dto;
    }
}
