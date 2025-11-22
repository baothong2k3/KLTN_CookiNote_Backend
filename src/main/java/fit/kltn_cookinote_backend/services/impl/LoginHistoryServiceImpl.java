/*
 * @ (#) LoginHistoryServiceImpl.java    1.0    20/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.PageResult;
import fit.kltn_cookinote_backend.dtos.response.UserLoginHistoryResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.entities.UserLoginHistory;
import fit.kltn_cookinote_backend.repositories.UserLoginHistoryRepository;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.LoginHistoryService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginHistoryServiceImpl implements LoginHistoryService {

    private final UserLoginHistoryRepository loginHistoryRepo;
    private final UserRepository userRepo;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(User user) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                String ipAddress = getClientIp(request);
                String userAgent = request.getHeader("User-Agent");

                UserLoginHistory history = UserLoginHistory.builder()
                        .user(user)
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .build();

                loginHistoryRepo.save(history);
            }
        } catch (Exception e) {
            // Chỉ log lỗi, không ném exception để tránh làm fail quy trình đăng nhập của user
            log.error("Lỗi khi lưu lịch sử đăng nhập cho user {}: {}", user.getUserId(), e.getMessage());
        }
    }

    /**
     * Helper để lấy IP chính xác, xử lý trường hợp đứng sau Proxy/Load Balancer
     */
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = "";
        if (request != null) {
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || remoteAddr.isEmpty()) {
                remoteAddr = request.getRemoteAddr();
            }
        }
        // Nếu có nhiều IP (do qua nhiều proxy), lấy IP đầu tiên (IP gốc của client)
        if (remoteAddr != null && remoteAddr.contains(",")) {
            remoteAddr = remoteAddr.split(",")[0].trim();
        }
        return remoteAddr;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<UserLoginHistoryResponse> getAllLoginHistory(Pageable pageable) {
        Page<UserLoginHistory> page = loginHistoryRepo.findAll(pageable);
        // Map entity sang DTO
        return PageResult.of(page.map(UserLoginHistoryResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<UserLoginHistoryResponse> getUserLoginHistory(Long userId, Pageable pageable) {
        // Kiểm tra user có tồn tại không
        if (!userRepo.existsById(userId)) {
            throw new EntityNotFoundException("User không tồn tại với id: " + userId);
        }

        Page<UserLoginHistory> page = loginHistoryRepo.findByUser_UserId(userId, pageable);
        return PageResult.of(page.map(UserLoginHistoryResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<UserLoginHistoryResponse> getMyLoginHistory(Long userId, Pageable pageable) {
        // Không cần kiểm tra user tồn tại vì userId lấy từ AuthenticationPrincipal (đã xác thực)
        Page<UserLoginHistory> page = loginHistoryRepo.findByUser_UserId(userId, pageable);
        return PageResult.of(page.map(UserLoginHistoryResponse::from));
    }
}