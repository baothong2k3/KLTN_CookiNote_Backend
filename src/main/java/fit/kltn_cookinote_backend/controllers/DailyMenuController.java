/*
 * @ (#) DailyMenuController.java    1.0    09/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 09/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.DailyMenuResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.services.DailyMenuService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/daily-menu")
public class DailyMenuController {

    private final DailyMenuService dailyMenuService;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<DailyMenuResponse>> getDailyMenu(
            @AuthenticationPrincipal User authUser,
            @RequestParam(name = "size", defaultValue = "6") int size,
            HttpServletRequest request
    ) {
        DailyMenuResponse response = dailyMenuService.generateDailyMenu(authUser.getUserId(), size);
        return ResponseEntity.ok(ApiResponse.success("Gợi ý thực đơn hằng ngày", response, request.getRequestURI()));
    }
}
