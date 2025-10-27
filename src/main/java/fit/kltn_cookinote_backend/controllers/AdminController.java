/*
 * @ (#) AdminController.java    1.0    15/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 15/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.UserDto;
import fit.kltn_cookinote_backend.dtos.request.ExportRequest;
import fit.kltn_cookinote_backend.dtos.request.UserDetailDto;
import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.PagedUserResponse;
import fit.kltn_cookinote_backend.services.ExcelExportService;
import fit.kltn_cookinote_backend.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;


@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserService userService;
    private final ExcelExportService excelExportService;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedUserResponse>> getAllUsers(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            HttpServletRequest httpReq) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<UserDto> userPage = userService.getAllUsers(pageable);
        PagedUserResponse responseData = PagedUserResponse.from(userPage);

        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người dùng thành công", responseData, httpReq.getRequestURI()));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserDetailDto>> getUserById(@PathVariable Long id, HttpServletRequest httpReq) {
        UserDetailDto userDetails = userService.getUserDetails(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin chi tiết người dùng thành công", userDetails, httpReq.getRequestURI()));
    }

    /**
     * API xuất toàn bộ công thức ra file Excel và lưu trên server. Chỉ Admin.
     * Nhận đường dẫn tùy chọn qua Request Body.
     */
    @PostMapping("/export/recipes") // Endpoint mới
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> exportAllRecipesMergedToFile(
                                                                             @RequestBody(required = false) ExportRequest request,
                                                                             HttpServletRequest httpReq) throws IOException {

        String savedFilePath = excelExportService.exportAllRecipesMergedToExcelFile(request);
        String message = "Xuất file Excel thành công. Đã lưu tại: " + savedFilePath;
        return ResponseEntity.ok(ApiResponse.success(message, savedFilePath, httpReq.getRequestURI()));
    }
}