/*
 * @ (#) AiScoreResponse.java    1.0    21/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 21/10/2025
 * @version: 1.0
 */

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiScoreResponse {
    private double mainIngredientMatchScore;
    private double overallMatchScore;
    private String justification;
}
