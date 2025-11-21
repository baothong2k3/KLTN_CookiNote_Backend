/*
 * @ (#) HistoryController.java    1.0    20/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.UserLoginHistoryResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.repositories.UserLoginHistoryRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class HistoryController {
    private final UserLoginHistoryRepository loginRepo;

    @GetMapping("/login")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UserLoginHistoryResponse>>> getMyLoginHistory(
            @AuthenticationPrincipal User user,
            HttpServletRequest httpReq
    ) {
        // 1. Lấy danh sách Entity từ DB
        var entities = loginRepo.findByUser_UserIdOrderByLoginTimeDesc(user.getUserId());

        // 2. Chuyển đổi sang DTO để cắt bỏ thông tin User thừa và tránh lỗi vòng lặp
        List<UserLoginHistoryResponse> data = entities.stream()
                .map(UserLoginHistoryResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử đăng nhập thành công", data, httpReq.getRequestURI()));
    }
}
