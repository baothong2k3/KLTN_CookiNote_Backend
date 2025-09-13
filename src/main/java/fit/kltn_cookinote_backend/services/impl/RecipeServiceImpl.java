/*
 * @ (#) RecipeServiceImpl.java    1.0    11/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 11/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.RecipeCreateRequest;
import fit.kltn_cookinote_backend.dtos.response.IdResponse;
import fit.kltn_cookinote_backend.entities.Category;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.CategoryRepository;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.RecipeService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecipeServiceImpl implements RecipeService {
    private final RecipeRepository recipeRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public IdResponse createByAdmin(Long adminUserId, RecipeCreateRequest req) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new EntityNotFoundException("Admin không tồn tại: " + adminUserId));
        if (admin.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Chỉ ADMIN mới được tạo recipe công khai");
        }
        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category không tồn tại: " + req.categoryId()));
        return null;
    }
}
