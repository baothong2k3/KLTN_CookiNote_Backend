/*
 * @ (#) IngredientCategoryService.java    1.0    19/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 19/12/2025
 * @version: 1.0
 */

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IngredientCategoryService {
    private final ObjectMapper objectMapper;
    private Map<String, String> categoryMap = new HashMap<>();

    @PostConstruct
    public void init() {
        try (InputStream is = new ClassPathResource("ingredient-categories.json").getInputStream()) {
            TypeReference<Map<String, String>> typeRef = new TypeReference<>() {};
            Map<String, String> rawData = objectMapper.readValue(is, typeRef);
            // Chuẩn hóa key về chữ thường
            rawData.forEach((k, v) -> categoryMap.put(k.toLowerCase().trim(), v));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getCategory(String ingredientName) {
        if (ingredientName == null) return "Khác";
        String key = ingredientName.toLowerCase().trim();
        // Tìm chính xác hoặc chứa từ khóa
        return categoryMap.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getKey().length(), e1.getKey().length()))
                .filter(entry -> key.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("Khác");
    }
}