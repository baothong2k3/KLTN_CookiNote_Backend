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
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.mappers.UserMapper;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepo;
    private final UserMapper userMapper;

    @Transactional
    public UserDto updateDisplayName(Long userId, UpdateDisplayNameRequest req) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

        String newName = req.displayName() == null ? "" : req.displayName().trim();
        if (newName.isEmpty()) {
            throw new IllegalArgumentException("Tên hiển thị không được để trống.");
        }
        if (newName.length() > 100) {
            throw new IllegalArgumentException("Tên hiển thị tối đa 100 ký tự.");
        }
        if (newName.equals(user.getDisplayName())) {
            return userMapper.toDto(user);
        }

        user.setDisplayName(newName);
        userRepo.save(user);

        return userMapper.toDto(user);
    }
}
