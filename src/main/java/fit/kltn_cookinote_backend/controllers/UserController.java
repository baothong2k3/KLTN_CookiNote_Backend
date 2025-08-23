/*
 * @ (#) UserController.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.UserDto;
import fit.kltn_cookinote_backend.dtos.request.UpdateDisplayNameRequest;
import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    // Kiểm tra access token
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(@AuthenticationPrincipal User user,
                                                               HttpServletRequest httpReq) {
        Map<String, Object> data = Map.of(
                "userId", user.getUserId(),
                "email", user.getEmail(),
                "displayName", user.getDisplayName(),
                "role", user.getRole().name()
        );
        return ResponseEntity.ok(ApiResponse.success("OK", data, httpReq.getRequestURI()));
    }

    @PatchMapping("/display-name")
    public ResponseEntity<ApiResponse<UserDto>> updateDisplayName(
            @AuthenticationPrincipal User authUser,
            @Valid @RequestBody UpdateDisplayNameRequest req,
            HttpServletRequest httpReq) {

        if (authUser == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Token đã hết hạn hoặc không hợp lệ", httpReq.getRequestURI()));
        }

        UserDto dto = userService.updateDisplayName(authUser.getUserId(), req);
        return ResponseEntity.ok(
                ApiResponse.success("Cập nhật displayName thành công.", dto, httpReq.getRequestURI())
        );
    }
}
