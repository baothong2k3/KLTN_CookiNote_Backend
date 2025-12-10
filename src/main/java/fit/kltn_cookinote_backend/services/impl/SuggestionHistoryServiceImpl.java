/*
 * @ (#) SuggestionHistoryServiceImpl.java    1.0    10/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 10/12/2025
 * @version: 1.0
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.kltn_cookinote_backend.dtos.SuggestionHistoryItem;
import fit.kltn_cookinote_backend.dtos.request.PersonalizedSuggestionRequest;
import fit.kltn_cookinote_backend.dtos.response.PageResult;
import fit.kltn_cookinote_backend.services.SuggestionHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuggestionHistoryServiceImpl implements SuggestionHistoryService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper; // Dùng để fallback convert nếu cần

    private static final String HISTORY_KEY_PREFIX = "user:history:suggestion:";
    private static final int MAX_HISTORY_SIZE = 50; // Giới hạn 50 mục gần nhất

    @Override
    public void save(Long userId, PersonalizedSuggestionRequest req) {
        String key = HISTORY_KEY_PREFIX + userId;

        try {
            // 1. Lấy TOÀN BỘ danh sách hiện tại để kiểm tra trùng lặp
            List<Object> existingItems = redisTemplate.opsForList().range(key, 0, -1);

            if (existingItems != null) {
                for (Object rawObj : existingItems) {
                    SuggestionHistoryItem item = convertToDto(rawObj);

                    // 2. Nếu tìm thấy request giống hệt (bất kể thời gian tìm kiếm cũ là khi nào)
                    if (item != null && item.getRequest().equals(req)) {
                        // Xóa object cũ đó ra khỏi Redis
                        redisTemplate.opsForList().remove(key, 1, rawObj);
                    }
                }
            }

            // 3. Tạo item mới (với thời gian hiện tại)
            SuggestionHistoryItem newItem = SuggestionHistoryItem.builder()
                    .request(req)
                    .searchedAt(LocalDateTime.now())
                    .build();

            // 4. Push item mới vào đầu danh sách (Mới nhất lên đầu)
            redisTemplate.opsForList().leftPush(key, newItem);

            // 5. Cắt danh sách để đảm bảo không vượt quá giới hạn (VD: giữ 50 cái mới nhất)
            redisTemplate.opsForList().trim(key, 0, MAX_HISTORY_SIZE - 1);

        } catch (Exception e) {
            log.error("Lỗi khi lưu lịch sử Redis cho user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Helper: Convert Object từ Redis sang SuggestionHistoryItem
     * Xử lý cả trường hợp Jackson trả về LinkedHashMap
     */
    private SuggestionHistoryItem convertToDto(Object obj) {
        if (obj instanceof SuggestionHistoryItem item) {
            return item;
        } else {
            try {
                return objectMapper.convertValue(obj, SuggestionHistoryItem.class);
            } catch (Exception e) {
                log.warn("Không thể convert item lịch sử: {}", obj);
                return null;
            }
        }
    }

    @Override
    public PageResult<SuggestionHistoryItem> getHistory(Long userId, int page, int size) {
        String key = HISTORY_KEY_PREFIX + userId;

        try {
            Long totalElementsLong = redisTemplate.opsForList().size(key);
            long totalElements = (totalElementsLong != null) ? totalElementsLong : 0;

            if (totalElements == 0) {
                return new PageResult<>(page, size, 0, 0, false, Collections.emptyList());
            }

            long start = (long) page * size;
            long end = start + size - 1;

            if (start >= totalElements) {
                return new PageResult<>(page, size, totalElements, (int) Math.ceil((double) totalElements / size), false, Collections.emptyList());
            }

            List<Object> rawList = redisTemplate.opsForList().range(key, start, end);

            List<SuggestionHistoryItem> items = new ArrayList<>();
            if (rawList != null) {
                for (Object obj : rawList) {
                    SuggestionHistoryItem item = convertToDto(obj);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }

            int totalPages = (int) Math.ceil((double) totalElements / size);
            boolean hasNext = (page + 1) < totalPages;

            return PageResult.<SuggestionHistoryItem>builder()
                    .page(page)
                    .size(size)
                    .totalElements(totalElements)
                    .totalPages(totalPages)
                    .hasNext(hasNext)
                    .items(items)
                    .build();

        } catch (Exception e) {
            log.error("Lỗi khi lấy lịch sử Redis cho user {}: {}", userId, e.getMessage());
            return new PageResult<>(page, size, 0, 0, false, Collections.emptyList());
        }
    }
}
