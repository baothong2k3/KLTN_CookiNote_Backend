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

        // Tạo object lưu trữ kèm thời gian
        SuggestionHistoryItem item = SuggestionHistoryItem.builder()
                .request(req)
                .searchedAt(LocalDateTime.now())
                .build();

        try {
            // 1. Push vào đầu danh sách (Left Push)
            redisTemplate.opsForList().leftPush(key, item);

            // 2. Cắt danh sách, chỉ giữ lại MAX_HISTORY_SIZE phần tử mới nhất
            // Redis index bắt đầu từ 0, nên giữ từ 0 đến (MAX - 1)
            redisTemplate.opsForList().trim(key, 0, MAX_HISTORY_SIZE - 1);

        } catch (Exception e) {
            // Chỉ log lỗi, không ném exception để không làm gián đoạn luồng chính của người dùng
            log.error("Lỗi khi lưu lịch sử Redis cho user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    public PageResult<SuggestionHistoryItem> getHistory(Long userId, int page, int size) {
        String key = HISTORY_KEY_PREFIX + userId;

        try {
            // a. Lấy tổng số phần tử
            Long totalElementsLong = redisTemplate.opsForList().size(key);
            long totalElements = (totalElementsLong != null) ? totalElementsLong : 0;

            if (totalElements == 0) {
                return new PageResult<>(page, size, 0, 0, false, Collections.emptyList());
            }

            // b. Tính toán start và end index cho LRANGE
            long start = (long) page * size;
            long end = start + size - 1;

            // Nếu start vượt quá tổng số phần tử -> trang trống
            if (start >= totalElements) {
                return new PageResult<>(page, size, totalElements, (int) Math.ceil((double) totalElements / size), false, Collections.emptyList());
            }

            // c. Lấy dữ liệu từ Redis
            List<Object> rawList = redisTemplate.opsForList().range(key, start, end);

            List<SuggestionHistoryItem> items = new ArrayList<>();
            if (rawList != null) {
                for (Object obj : rawList) {
                    if (obj instanceof SuggestionHistoryItem item) {
                        items.add(item);
                    } else {
                        // Fallback: Nếu Redis trả về LinkedHashMap do Jackson deserialize
                        try {
                            items.add(objectMapper.convertValue(obj, SuggestionHistoryItem.class));
                        } catch (Exception e) {
                            log.warn("Không thể convert item lịch sử: {}", obj);
                        }
                    }
                }
            }

            // d. Tính toán thông tin phân trang
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
            // Trả về danh sách rỗng nếu lỗi Redis
            return new PageResult<>(page, size, 0, 0, false, Collections.emptyList());
        }
    }
}
