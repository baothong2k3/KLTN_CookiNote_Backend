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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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

    // Admin xem toàn bộ
    @Override
    @Transactional(readOnly = true)
    public PageResult<UserLoginHistoryResponse> getAllLoginHistory(LocalDate date, Pageable pageable) {
        Page<UserLoginHistory> page;

        if (date != null) {
            // Tạo khoảng thời gian từ 00:00:00 đến 23:59:59.999999999 của ngày đó
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);
            page = loginHistoryRepo.findByLoginTimeBetween(start, end, pageable);
        } else {
            page = loginHistoryRepo.findAll(pageable);
        }

        return PageResult.of(page.map(UserLoginHistoryResponse::from));
    }

    // Admin xem chi tiết User
    @Override
    @Transactional(readOnly = true)
    public PageResult<UserLoginHistoryResponse> getUserLoginHistory(Long userId, LocalDate date, Pageable pageable) {
        if (!userRepo.existsById(userId)) {
            throw new EntityNotFoundException("User không tồn tại với id: " + userId);
        }
        return getHistoryInternal(userId, date, pageable);
    }

    // User tự xem của mình
    @Override
    @Transactional(readOnly = true)
    public PageResult<UserLoginHistoryResponse> getMyLoginHistory(Long userId, LocalDate date, Pageable pageable) {
        return getHistoryInternal(userId, date, pageable);
    }

    // Helper function để tránh lặp code giữa Admin xem User và User tự xem
    private PageResult<UserLoginHistoryResponse> getHistoryInternal(Long userId, LocalDate date, Pageable pageable) {
        Page<UserLoginHistory> page;

        if (date != null) {
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);
            page = loginHistoryRepo.findByUser_UserIdAndLoginTimeBetween(userId, start, end, pageable);
        } else {
            page = loginHistoryRepo.findByUser_UserId(userId, pageable);
        }

        return PageResult.of(page.map(UserLoginHistoryResponse::from));
    }
}