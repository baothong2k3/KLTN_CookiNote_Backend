/*
 * @ (#) DailyMenuService.java    1.0    09/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 09/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.DailyMenuResponse;

public interface DailyMenuService {
    DailyMenuResponse generateDailyMenu(Long userId, int size);
}