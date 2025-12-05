/*
 * @ (#) PersonalizedSuggestionRequest.java    1.0    05/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 05/12/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.enums.MealType;
import jakarta.validation.constraints.Min;

public record PersonalizedSuggestionRequest(
        // 1. Mong muốn cụ thể
        @Min(value = 50, message = "Calo tối thiểu là 50")
        Integer targetCalories, // Nếu null sẽ tự tính

        @Min(value = 1, message = "Khẩu phần tối thiểu là 1")
        Integer servings,       // Mặc định là 1 nếu null

        String healthCondition, // VD: "tiểu đường", "cao huyết áp"
        String dishCharacteristics, // VD: "món nước", "giải nhiệt", "chua cay"
        MealType mealType,      // Sáng, trưa, tối...

        // 2. Thông tin thể trạng (để tính TDEE nếu không nhập calo)
        Double height, // cm
        Double weight, // kg
        Integer age,
        String gender, // "MALE", "FEMALE"
        String activityLevel // "LOW", "MODERATE", "HIGH"
) {}
