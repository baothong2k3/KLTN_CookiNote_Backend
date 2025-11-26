/*
 * @ (#) LogStreamService.java    1.0    27/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/11/2025
 * @version: 1.0
 */

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;

@Service
public class LogStreamService {

    // Danh sách các kết nối SSE đang mở (Thread-safe)
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter createEmitter() {
        // Timeout: 30 phút (hoặc tuỳ chỉnh), sau đó client phải reconnect
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        this.emitters.add(emitter);

        // Khi kết thúc hoặc lỗi thì loại bỏ khỏi danh sách
        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));
        emitter.onError((e) -> this.emitters.remove(emitter));

        return emitter;
    }

    public void broadcast(String logMessage) {
        // Gửi log cho tất cả admin đang kết nối
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(logMessage));
            } catch (IOException e) {
                // Nếu lỗi (client ngắt kết nối đột ngột), xóa emitter
                this.emitters.remove(emitter);
            }
        }
    }

    // Static instance để Appender (không phải Spring Bean) có thể gọi được
    @Getter
    private static LogStreamService instance;

    public LogStreamService() {
        instance = this;
    }
}
