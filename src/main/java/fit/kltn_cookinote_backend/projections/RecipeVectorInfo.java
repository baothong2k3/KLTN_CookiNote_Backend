/*
 * @ (#) RecipeVectorInfo.java    1.0    05/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.projections;/*
 * @description:
 * @author: Bao Thong
 * @date: 05/12/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.enums.Privacy;

public interface RecipeVectorInfo {
    Long getId();
    Long getOwnerId(); // Alias từ r.user.userId
    Privacy getPrivacy();
    String getEmbeddingVector();
}
