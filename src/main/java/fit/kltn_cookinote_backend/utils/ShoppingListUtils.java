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

import java.util.Locale;

public class ShoppingListUtils {

    public static String normalize(String s) {
        return canonicalize(s).toLowerCase(Locale.ROOT);
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
        if (q.length() > 50) throw new IllegalArgumentException("Số lượng tối đa 50 ký tự.");
        return q;
    }


}
