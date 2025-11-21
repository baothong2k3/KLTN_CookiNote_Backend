/*
 * @ (#) LoginHistoryService.java    1.0    20/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.User;

public interface LoginHistoryService {
    /**
     * Lưu lịch sử đăng nhập của user.
     * Hàm này sẽ tự động lấy IP và User-Agent từ RequestContext hiện tại.
     *
     * @param user Người dùng vừa đăng nhập thành công.
     */
    void save(User user);
}
