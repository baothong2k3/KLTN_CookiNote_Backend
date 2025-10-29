/*
 * @ (#) SuggestRecipesRequest.java    1.0    21/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 21/10/2025
 * @version: 1.0
 */

import java.util.List;

public record SuggestRecipesRequest(List<String> ingredientNames) {
}