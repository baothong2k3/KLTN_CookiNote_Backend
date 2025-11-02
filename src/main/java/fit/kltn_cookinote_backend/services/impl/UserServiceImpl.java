/*
 * @ (#) UserServiceImpl.java    1.0    26/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 26/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.UserDto;
import fit.kltn_cookinote_backend.dtos.request.UpdateDisplayNameRequest;
import fit.kltn_cookinote_backend.dtos.request.UserDetailDto;
import fit.kltn_cookinote_backend.dtos.response.UserStatsResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.AuthProvider;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.mappers.UserMapper;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.RefreshTokenService;
import fit.kltn_cookinote_backend.services.SessionAllowlistService;
import fit.kltn_cookinote_backend.services.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepo;
    private final RecipeRepository recipeRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder encoder;
    private final RefreshTokenService refreshService;
    private final SessionAllowlistService sessionService;

    @Override
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

    @Override
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

        // Chỉ cho tài khoản LOCAL đổi mật khẩu nội bộ
        if (user.getAuthProvider() != AuthProvider.LOCAL || user.getPassword() == null) {
            throw new IllegalStateException("Tài khoản không hỗ trợ đổi mật khẩu nội bộ.");
        }

        // Kiểm tra mật khẩu hiện tại
        if (!encoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
        }

        // Không cho đặt trùng
        if (encoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu cũ.");
        }

        // Cập nhật mật khẩu
        user.setPassword(encoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now(ZoneOffset.UTC)); // khuyến nghị
        userRepo.save(user);

        // Revoke ALL tokens của user: buộc đăng nhập lại ở mọi nơi
        refreshService.revokeAllForUser(userId);
        sessionService.revokeAllForUser(userId);
    }

    @Override
    public boolean checkPassword(Long userId, String currentPassword) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));
        if (user.getAuthProvider() != AuthProvider.LOCAL || user.getPassword() == null) {
            throw new IllegalStateException("Tài khoản không hỗ trợ kiểm tra mật khẩu nội bộ.");
        }
        return encoder.matches(currentPassword, user.getPassword());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserDto> getAllUsers(Pageable pageable) {
        Page<User> userPage = userRepo.findAll(pageable);
        return userPage.map(userMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetailDto getUserDetails(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với id: " + userId));
        return userMapper.toDetailDto(user);
    }

    @Override
    @Transactional
    public UserDetailDto disableUser(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với id: " + userId));

        // Ngăn chặn việc vô hiệu hóa tài khoản Admin khác
        if (user.getRole() == Role.ADMIN) {
            throw new AccessDeniedException("Không thể vô hiệu hóa tài khoản Admin.");
        }

        if (!user.isEnabled()) {
            // Nếu đã bị vô hiệu hóa rồi thì không cần làm gì, trả về thông tin hiện tại
            return userMapper.toDetailDto(user);
        }

        user.setEnabled(false); // Đặt trạng thái thành false
        userRepo.save(user);

        // Thu hồi tất cả phiên đăng nhập và refresh token của người dùng này
        refreshService.revokeAllForUser(userId);
        sessionService.revokeAllForUser(userId);

        return userMapper.toDetailDto(user); // Trả về thông tin chi tiết đã cập nhật
    }

    @Override
    @Transactional
    public UserDetailDto enableUser(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với id: " + userId));

        // Có thể thêm kiểm tra để đảm bảo chỉ Admin mới kích hoạt được,
        // nhưng @PreAuthorize ở Controller đã đủ an toàn.

        if (user.isEnabled()) {
            // Nếu tài khoản đã được kích hoạt rồi thì không cần làm gì
            return userMapper.toDetailDto(user);
        }

        // Chỉ kích hoạt lại nếu email đã được xác thực trước đó
        if (!user.isEmailVerified()) {
            throw new IllegalStateException("Không thể kích hoạt tài khoản chưa xác thực email. Người dùng cần xác thực email trước.");
        }

        user.setEnabled(true); // Đặt trạng thái thành true
        userRepo.save(user);

        // Lưu ý: Không cần thu hồi token khi enable lại. Người dùng có thể đăng nhập bình thường.

        return userMapper.toDetailDto(user); // Trả về thông tin chi tiết đã cập nhật
    }

    @Override
    @Transactional(readOnly = true)
    public UserStatsResponse getUserStats() {
        long totalUsers = userRepo.count();
        long totalRecipes = recipeRepository.count(); // Đếm tất cả công thức
        long activeUsers = userRepo.countByEnabledTrue();
        long newUsersToday = userRepo.countNewUsersToday();

        return UserStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalRecipes(totalRecipes)
                .activeUsers(activeUsers)
                .newUsersToday(newUsersToday)
                .build();
    }
}
