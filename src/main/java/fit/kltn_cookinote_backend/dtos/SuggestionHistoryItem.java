/*
 * @ (#) SuggestionHistoryItem.java    1.0    10/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos;/*
 * @description:
 * @author: Bao Thong
 * @date: 10/12/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.PersonalizedSuggestionRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionHistoryItem {
    private PersonalizedSuggestionRequest request; // Nội dung request đã nhập
    private LocalDateTime searchedAt;              // Thời điểm tìm kiếm
}
