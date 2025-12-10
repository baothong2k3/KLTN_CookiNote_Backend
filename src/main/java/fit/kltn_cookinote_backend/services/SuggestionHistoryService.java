/*
 * @ (#) SuggestionHistoryService.java    1.0    10/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 10/12/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.SuggestionHistoryItem;
import fit.kltn_cookinote_backend.dtos.request.PersonalizedSuggestionRequest;
import fit.kltn_cookinote_backend.dtos.response.PageResult;

public interface SuggestionHistoryService {
    /**
     * Lưu lịch sử tìm kiếm của người dùng vào Redis.
     * @param userId ID người dùng
     * @param req Nội dung request gợi ý
     */
    void save(Long userId, PersonalizedSuggestionRequest req);

    /**
     * Lấy danh sách lịch sử tìm kiếm từ Redis (có phân trang).
     * @param userId ID người dùng
     * @param page Số trang (bắt đầu từ 0)
     * @param size Kích thước trang
     * @return PageResult chứa danh sách lịch sử
     */
    PageResult<SuggestionHistoryItem> getHistory(Long userId, int page, int size);
}
