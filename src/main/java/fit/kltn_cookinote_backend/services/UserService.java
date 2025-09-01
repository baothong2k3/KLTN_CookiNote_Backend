/*
 * @ (#) UserService.java    1.0    23/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 23/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.UserDto;
import fit.kltn_cookinote_backend.dtos.request.UpdateDisplayNameRequest;
import org.springframework.stereotype.Service;

@Service
public interface UserService {
    UserDto updateDisplayName(Long userId, UpdateDisplayNameRequest req);

    void changePassword(Long userId, String currentPassword, String newPassword);

    boolean checkPassword(Long userId, String currentPassword);
}
