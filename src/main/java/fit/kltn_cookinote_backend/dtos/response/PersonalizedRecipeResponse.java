/*
 * @ (#) PersonalizedRecipeResponse.java    1.0    05/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 05/12/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.enums.Difficulty;
import lombok.Builder;

import java.io.Serializable;
import java.util.List;

/**
 * DTO trả về cho chức năng gợi ý thực đơn cá nhân hóa.
 * Tương tự GeneratedRecipeResponse nhưng có thêm context từ công thức gốc.
 */
@Builder
public record PersonalizedRecipeResponse(
        Long originalRecipeId, // ID công thức gốc để user click vào xem chi tiết
        String title,          // Tên món (AI gợi ý hoặc gốc)
        String description,    // Lý do tại sao phù hợp (AI tạo)
        String imageUrl,       // Ảnh từ công thức gốc
        Integer prepareTime,   // Gốc
        Integer cookTime,      // Gốc
        Difficulty difficulty, // Gốc
        Integer calories,      // AI tính toán lại theo khẩu phần
        Integer servings,      // Khẩu phần user yêu cầu
        List<IngredientDto> ingredients, // Nguyên liệu đã điều chỉnh định lượng
        List<StepDto> steps    // Các bước làm từ công thức gốc
) implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    @Builder
    public record IngredientDto(
            String name,
            String quantity
    ) implements Serializable {
    }

    @Builder
    public record StepDto(
            Integer stepNo,
            String content,
            String tips,
            Integer suggestedTime
    ) implements Serializable {
    }
}