/*
 * @ (#) ShareService.java    1.0    28/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 28/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.RecipeResponse;
import fit.kltn_cookinote_backend.dtos.response.ShareResponse;

public interface ShareService {

    /**
     * Tạo link chia sẻ và mã QR cho một công thức.
     *
     * @param sharerUserId ID người thực hiện chia sẻ
     * @param recipeId     ID công thức cần chia sẻ
     * @return ShareResponse chứa thông tin chia sẻ
     */
    ShareResponse createShareLink(Long sharerUserId, Long recipeId);

    /**
     * Lấy thông tin chi tiết công thức dạng RecipeResponse dựa trên mã chia sẻ.
     *
     * @param shareCode          Mã chia sẻ
     * @param viewerUserIdOrNull ID của người xem (nếu đã đăng nhập), dùng để kiểm tra favorite
     * @return RecipeResponse nếu tìm thấy và hợp lệ, nếu không ném Exception
     */
    RecipeResponse getRecipeByShareCode(String shareCode, Long viewerUserIdOrNull);
}
