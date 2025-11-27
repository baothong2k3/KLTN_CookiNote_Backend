/*
 * @ (#) IngredientSynonymService.java    1.0    08/11/2025
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
import fit.kltn_cookinote_backend.utils.ShoppingListUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dịch vụ này tải một từ điển đồng nghĩa (synonyms) cho nguyên liệu
 * và cung cấp một phương thức để chuẩn hóa các biến thể về một tên gốc.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngredientSynonymService {

    private final ObjectMapper objectMapper;

    // Key: Tên biến thể (đã normalize), Value: Tên chuẩn (đã normalize)
    // Ví dụ: "bot mi da dung" -> "bot mi"
    // Ví dụ: "nuoc duong" -> "duong"
    private Map<String, String> synonymMap = Collections.emptyMap();

    private static final String SYNONYM_FILE = "ingredient-synonyms.json";

    @PostConstruct
    public void loadData() {
        try (InputStream inputStream = new ClassPathResource(SYNONYM_FILE).getInputStream()) {

            // 1. Đọc file JSON: Map<String, List<String>>
            TypeReference<Map<String, List<String>>> typeRef = new TypeReference<>() {
            };
            Map<String, List<String>> rawData = objectMapper.readValue(inputStream, typeRef);

            // 2. Đảo ngược Map để tra cứu
            Map<String, String> processedMap = new HashMap<>();

            for (Map.Entry<String, List<String>> entry : rawData.entrySet()) {
                // Tên chuẩn (ví dụ: "bột mì")
                String standardName = ShoppingListUtils.normalize(entry.getKey());

                // Bản thân tên chuẩn cũng là 1 key
                processedMap.put(standardName, standardName);

                // Các biến thể (ví dụ: "bột mì đa dụng", "nước đường")
                for (String variant : entry.getValue()) {
                    String normalizedVariant = ShoppingListUtils.normalize(variant);
                    if (!processedMap.containsKey(normalizedVariant)) {
                        processedMap.put(normalizedVariant, standardName);
                    } else {
                        log.debug("Từ đồng nghĩa '{}' (chuẩn hóa: '{}') được định nghĩa cho nhiều hơn 1 từ chuẩn. Giữ lại giá trị cũ.",
                                variant, normalizedVariant);
                    }
                }
            }

            this.synonymMap = Collections.unmodifiableMap(processedMap);
            log.info("Tải thành công {} từ đồng nghĩa (đã đảo ngược) từ {}", this.synonymMap.size(), SYNONYM_FILE);

        } catch (Exception e) {
            log.error("Không thể tải file từ đồng nghĩa: {}. Sẽ sử dụng normalize cơ bản.", SYNONYM_FILE, e);
            this.synonymMap = Collections.emptyMap();
        }
    }

    /**
     * Chuẩn hóa tên nguyên liệu về dạng gốc (nếu có trong từ điển).
     * Nếu không có trong từ điển, trả về tên đã được normalize cơ bản.
     * * @param rawName Tên nguyên liệu thô (ví dụ: "Bột mì đa dụng (Vỏ bánh)")
     *
     * @return Tên đã chuẩn hóa (ví dụ: "bot mi")
     */
    public String getStandardizedName(String rawName) {
        // 1. Normalize cơ bản (bỏ dấu, bỏ ngoặc,...)
        String normalized = ShoppingListUtils.normalize(rawName);

        // 2. Tra cứu trong từ điển đồng nghĩa
        // Nếu "bot mi da dung" có trong map, nó sẽ trả về "bot mi"
        // Nếu "thit ba chi" có trong map, nó sẽ trả về "thit lon"
        // Nếu "cai thia" không có trong map, nó sẽ trả về "cai thia"
        return synonymMap.getOrDefault(normalized, normalized);
    }
}
