/*
 * @ (#) ImageController.java    1.0    21/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 21/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.DeleteImagesRequest;
import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.services.ImageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    /**
     * Xóa vĩnh viễn các ảnh (bìa hoặc bước) đang ở trạng thái 'active: false'.
     * Yêu cầu quyền Admin hoặc chủ sở hữu của ảnh.
     */
    @DeleteMapping("/delete-inactive")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> deleteInactiveImages(
            @AuthenticationPrincipal User authUser,
            @Valid @RequestBody DeleteImagesRequest req,
            HttpServletRequest httpReq) {

        Map<String, Integer> result = imageService.deleteInactiveImages(
                authUser,
                req.coverImageIds(),
                req.stepImageIds()
        );

        String message = String.format("Đã xóa %d ảnh bìa và %d ảnh bước không hoạt động.",
                result.getOrDefault("deletedCovers", 0),
                result.getOrDefault("deletedSteps", 0));

        return ResponseEntity.ok(ApiResponse.success(message, result, httpReq.getRequestURI()));
    }
}
