/*
 * @ (#) ShoppingListUtils.java    1.0    28/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.utils;/*
 * @description:
 * @author: Bao Thong
 * @date: 28/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.ShoppingListResponse;
import fit.kltn_cookinote_backend.entities.ShoppingList;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class ShoppingListUtils {

    // Pattern để loại bỏ dấu tiếng Việt
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    // Pattern để loại bỏ các từ trong ngoặc đơn (Vỏ bánh), (Nhân),...
    private static final Pattern PARENTHESIS_PATTERN = Pattern.compile("\\s*\\([^)]*\\)\\s*");

    // Pattern để loại bỏ các từ bổ nghĩa cuối (đã mở rộng)
    private static final Pattern SUFFIX_WORDS_PATTERN = Pattern.compile(
            "\\s+(non|tươi|khô|nhỏ|to|già|sống|chín|ngon|xay|băm|lát|viên|rang|xé|thái|ngâm|luộc|bằm|nhuyễn|xắt|cắt)$"
    );

    /**
     * Chuẩn hóa CƠ BẢN: Bỏ dấu, lowercase, bỏ ngoặc đơn, bỏ từ phụ.
     * Ví dụ: "Bột mì đa dụng (Vỏ bánh)" -> "bot mi da dung"
     * Ví dụ: "tép đồng nhỏ" -> "tep dong"
     * * HÀM NÀY SẼ ĐƯỢC GỌI BỞI IngredientSynonymService
     */
    public static String normalize(String s) {
        if (s == null) return "";

        // 1. Gộp khoảng trắng và bỏ ngoặc đơn
        // "Bột mì đa dụng (Vỏ bánh)" -> "Bột mì đa dụng"
        String cleaned = canonicalize(s);
        cleaned = PARENTHESIS_PATTERN.matcher(cleaned).replaceAll(""); //

        // 2. Bỏ các từ hậu tố không quan trọng
        // "tép đồng nhỏ" -> "tép đồng"
        cleaned = SUFFIX_WORDS_PATTERN.matcher(cleaned).replaceAll(""); //

        // 3. Chuyển về chữ thường
        cleaned = cleaned.toLowerCase(Locale.ROOT);

        // 4. Bỏ dấu (ví dụ: "bột mì đa dụng" -> "bot mi da dung")
        String normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFD);
        normalized = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.replaceAll("[đĐ]", "d"); // Xử lý chữ 'đ'

        return normalized.trim();
    }

    public static String canonicalize(String s) {
        if (s == null) return "";
        // trim + gộp khoảng trắng
        return s.trim().replaceAll("\\s+", " ");
    }

    public static String safe(String s) {
        return s == null ? "" : s;
    }

    public static ShoppingListResponse toResponse(ShoppingList s, Long recipeId) {
        return ShoppingListResponse.builder()
                .id(s.getId())
                .recipeId(recipeId)
                .ingredient(s.getIngredient())
                .quantity(s.getQuantity())
                .checked(Boolean.TRUE.equals(s.getChecked()))
                .isFromRecipe(Boolean.TRUE.equals(s.getIsFromRecipe()))
                .build();
    }

    public static String normalizeAndValidateName(String raw) {
        String name = canonicalize(raw);
        if (name.isBlank()) throw new IllegalArgumentException("Tên nguyên liệu không được trống.");
        if (name.length() > 100) throw new IllegalArgumentException("Tên nguyên liệu tối đa 100 ký tự.");
        return name;
    }

    public static String normalizeAndValidateQuantity(String raw) {
        if (raw == null) return null;
        String q = canonicalize(raw);
        if (q.length() > 100) throw new IllegalArgumentException("Số lượng tối đa 100 ký tự.");
        return q;
    }
}