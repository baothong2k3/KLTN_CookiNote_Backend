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
import fit.kltn_cookinote_backend.dtos.response.PageResult;
import fit.kltn_cookinote_backend.dtos.response.UserLoginHistoryResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.repositories.UserLoginHistoryRepository;
import fit.kltn_cookinote_backend.services.LoginHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class HistoryController {
    private final LoginHistoryService loginHistoryService;

    @GetMapping("/login")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResult<UserLoginHistoryResponse>>> getMyLoginHistory(
                                                                                                @AuthenticationPrincipal User user,
                                                                                                @RequestParam(value = "page", defaultValue = "0") int page, // [Thêm] Param page
                                                                                                @RequestParam(value = "size", defaultValue = "10") int size, // [Thêm] Param size
                                                                                                HttpServletRequest httpReq
    ) {
        // Tạo Pageable với sắp xếp mặc định là mới nhất trước
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "loginTime"));

        // Gọi Service
        PageResult<UserLoginHistoryResponse> data = loginHistoryService.getMyLoginHistory(user.getUserId(), pageable);

        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử đăng nhập thành công", data, httpReq.getRequestURI()));
    }
}
