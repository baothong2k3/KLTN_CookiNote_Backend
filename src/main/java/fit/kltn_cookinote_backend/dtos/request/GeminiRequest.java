/*
 * @ (#) GeminiRequest.java    1.0    21/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 21/10/2025
 * @version: 1.0
 */

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GeminiRequest {
    private List<Content> contents;
    @JsonProperty("generationConfig") // Yêu cầu Gemini trả về JSON
    private GenerationConfig generationConfig;

    @Data
    @AllArgsConstructor
    public static class Content {
        private List<Part> parts;
    }

    @Data
    @AllArgsConstructor
    public static class Part {
        private String text;
    }

    @Data
    @AllArgsConstructor
    public static class GenerationConfig {
        private ResponseMimeType responseMimeType;
    }

    public enum ResponseMimeType {
        @JsonProperty("application/json")
        APPLICATION_JSON
    }

    // Helper method để tạo request nhanh
    public static GeminiRequest fromPrompt(String prompt) {
        Part part = new Part(prompt);
        Content content = new Content(List.of(part));
        GenerationConfig config = new GenerationConfig(ResponseMimeType.APPLICATION_JSON);
        return new GeminiRequest(List.of(content), config);
    }
}
