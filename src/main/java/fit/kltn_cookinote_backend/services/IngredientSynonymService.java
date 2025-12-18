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
import java.util.*;

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

    private Map<String, List<String>> standardToVariantsMap = Collections.emptyMap();

    private static final String SYNONYM_FILE = "ingredient-synonyms.json";

    @PostConstruct
    public void loadData() {
        try (InputStream inputStream = new ClassPathResource(SYNONYM_FILE).getInputStream()) {
            TypeReference<Map<String, List<String>>> typeRef = new TypeReference<>() {};
            Map<String, List<String>> rawData = objectMapper.readValue(inputStream, typeRef);

            Map<String, String> processedMap = new HashMap<>();

            // Map mới để lưu quan hệ 1 chiều từ Chuẩn -> Biến thể
            Map<String, List<String>> reverseMap = new HashMap<>();

            for (Map.Entry<String, List<String>> entry : rawData.entrySet()) {
                String standardName = ShoppingListUtils.normalize(entry.getKey());

                // Lưu danh sách biến thể cho từ chuẩn này (bao gồm cả chính nó)
                List<String> variants = new ArrayList<>();
                variants.add(standardName); // Thêm chính nó

                // Thêm bản thân nó vào map tra cứu xuôi
                processedMap.put(standardName, standardName);

                for (String variant : entry.getValue()) {
                    String normalizedVariant = ShoppingListUtils.normalize(variant);
                    variants.add(normalizedVariant); // Thêm biến thể vào list

                    if (!processedMap.containsKey(normalizedVariant)) {
                        processedMap.put(normalizedVariant, standardName);
                    }
                }

                reverseMap.put(standardName, variants);
            }

            this.synonymMap = Collections.unmodifiableMap(processedMap);
            this.standardToVariantsMap = Collections.unmodifiableMap(reverseMap); // Lưu lại

            log.info("Tải thành công dữ liệu đồng nghĩa.");
        } catch (Exception e) {
            log.error("Lỗi tải file đồng nghĩa", e);
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

    /**
     * Lấy tất cả các biến thể từ một từ khóa bất kỳ.
     * VD input "thịt lợn" -> ["thịt lợn", "thịt lợn quay", "sườn non"...]
     * VD input "thịt lợn quay" -> (chuẩn hóa về "thịt lợn") -> ["thịt lợn", "thịt lợn quay"...]
     */
    public List<String> getAllVariants(String rawName) {
        String standard = getStandardizedName(rawName); // Quy về chuẩn trước: "thịt lợn quay" -> "thịt lợn"
        return standardToVariantsMap.getOrDefault(standard, List.of(ShoppingListUtils.normalize(rawName)));
    }
}
