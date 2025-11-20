/*
 * @ (#) RecipeImportServiceImpl.java    1.0    20/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/11/2025
 * @version: 1.0
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.kltn_cookinote_backend.dtos.request.ImportRecipeRequest;
import fit.kltn_cookinote_backend.dtos.response.GeneratedRecipeResponse;
import fit.kltn_cookinote_backend.enums.Difficulty;
import fit.kltn_cookinote_backend.services.RecipeImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeImportServiceImpl implements RecipeImportService {

    private static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128 Safari/537.36";
    private static final int TIMEOUT_MS = 15000;
    private static final Set<String> SUPPORTED_HOSTS = Set.of("monngonmoingay.com", "www.monngonmoingay.com");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)");

    private final ObjectMapper objectMapper;

    @Override
    public GeneratedRecipeResponse importFromUrl(ImportRecipeRequest request) {
        String normalizedUrl = normalizeUrl(request.url());
        validateSupportedHost(normalizedUrl);

        Document document = fetchDocument(normalizedUrl);
        JsonNode recipeNode = extractRecipeNode(document);
        if (recipeNode == null) {
            throw new IllegalStateException("Không tìm thấy dữ liệu công thức trong trang đã cung cấp.");
        }

        GeneratedRecipeResponse response = mapToResponse(recipeNode);
        fillFallbacksIfNeeded(response, document);
        log.info("Import công thức từ URL '{}' thành công", normalizedUrl);
        return response;
    }

    private Document fetchDocument(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
        } catch (IOException e) {
            log.error("Không thể tải nội dung từ URL {}. Lỗi: {}", url, e.getMessage());
            throw new RuntimeException("Không thể tải nội dung từ đường dẫn đã cung cấp. Vui lòng thử lại sau.");
        }
    }

    private String normalizeUrl(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("URL không hợp lệ.");
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        return trimmed;
    }

    private void validateSupportedHost(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                throw new IllegalArgumentException("URL không hợp lệ.");
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            boolean supported = SUPPORTED_HOSTS.stream().anyMatch(lowerHost::endsWith);
            if (!supported) {
                throw new IllegalArgumentException("Hiện chỉ hỗ trợ import từ https://monngonmoingay.com.");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("URL không hợp lệ: " + ex.getMessage());
        }
    }

    private JsonNode extractRecipeNode(Document document) {
        Elements scripts = document.select("script[type=application/ld+json]");
        for (Element script : scripts) {
            String json = script.data();
            if (json == null || json.isBlank()) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(json);
                JsonNode recipeNode = findRecipeNode(node);
                if (recipeNode != null) {
                    return recipeNode;
                }
            } catch (Exception ex) {
                log.debug("Không thể parse JSON-LD: {}", ex.getMessage());
            }
        }
        return null;
    }

    private JsonNode findRecipeNode(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findRecipeNode(child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        JsonNode typeNode = node.get("@type");
        if (typeNode != null) {
            if (typeNode.isTextual() && "recipe".equalsIgnoreCase(typeNode.asText())) {
                return node;
            }
            if (typeNode.isArray()) {
                for (JsonNode child : typeNode) {
                    if (child.isTextual() && "recipe".equalsIgnoreCase(child.asText())) {
                        return node;
                    }
                }
            }
        }
        if (node.has("mainEntity")) {
            JsonNode found = findRecipeNode(node.get("mainEntity"));
            if (found != null) {
                return found;
            }
        }
        if (node.has("@graph")) {
            return findRecipeNode(node.get("@graph"));
        }
        return null;
    }

    private GeneratedRecipeResponse mapToResponse(JsonNode recipeNode) {
        GeneratedRecipeResponse response = new GeneratedRecipeResponse();
        response.setTitle(textOrNull(recipeNode.get("name")));
        response.setDescription(textOrNull(recipeNode.get("description")));
        response.setPrepareTime(parseDurationToMinutes(recipeNode.get("prepTime")));
        response.setCookTime(parseDurationToMinutes(recipeNode.get("cookTime")));
        response.setDifficulty(parseDifficulty(recipeNode.get("recipeCategory")));
        response.setIngredients(parseIngredients(recipeNode.get("recipeIngredient")));
        response.setSteps(parseSteps(recipeNode.get("recipeInstructions")));

        if (response.getIngredients() == null) {
            response.setIngredients(List.of());
        }
        if (response.getSteps() == null) {
            response.setSteps(List.of());
        }
        return response;
    }

    private Difficulty parseDifficulty(JsonNode categoryNode) {
        if (categoryNode == null || categoryNode.isNull()) {
            return null;
        }
        String value = categoryNode.isArray() ? textOrNull(categoryNode.get(0)) : textOrNull(categoryNode);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "de dang", "dễ", "de" -> Difficulty.EASY;
            case "kho", "khó" -> Difficulty.HARD;
            default -> Difficulty.MEDIUM;
        };
    }

    private List<GeneratedRecipeResponse.IngredientDto> parseIngredients(JsonNode ingredientNode) {
        if (ingredientNode == null || ingredientNode.isNull()) {
            return List.of();
        }
        List<GeneratedRecipeResponse.IngredientDto> result = new ArrayList<>();
        if (ingredientNode.isArray()) {
            for (JsonNode item : ingredientNode) {
                String text = extractIngredientText(item);
                GeneratedRecipeResponse.IngredientDto dto = buildIngredient(text);
                if (dto != null) {
                    result.add(dto);
                }
            }
        } else {
            GeneratedRecipeResponse.IngredientDto dto = buildIngredient(textOrNull(ingredientNode));
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    private String extractIngredientText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.has("text")) {
            return textOrNull(node.get("text"));
        }
        return textOrNull(node.get("name"));
    }

    private GeneratedRecipeResponse.IngredientDto buildIngredient(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("\\s+", " ").replaceFirst("^[\\-•]+\\s*", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        GeneratedRecipeResponse.IngredientDto dto = new GeneratedRecipeResponse.IngredientDto();
        String quantity = null;
        String name = cleaned;
        int separatorIdx = findSeparatorIndex(cleaned);
        if (separatorIdx > 0) {
            quantity = cleaned.substring(0, separatorIdx).trim();
            name = cleaned.substring(separatorIdx + 1).trim();
        } else if (Character.isDigit(cleaned.charAt(0))) {
            int firstSpace = cleaned.indexOf(' ');
            if (firstSpace > 0) {
                quantity = cleaned.substring(0, firstSpace).trim();
                name = cleaned.substring(firstSpace + 1).trim();
            }
        }
        dto.setName(name);
        dto.setQuantity(quantity);
        return dto;
    }

    private int findSeparatorIndex(String text) {
        int colon = text.indexOf(':');
        if (colon > 0) {
            return colon;
        }
        int dash = text.indexOf('-');
        if (dash > 0) {
            return dash;
        }
        int enDash = text.indexOf('–');
        if (enDash > 0) {
            return enDash;
        }
        return -1;
    }

    private List<GeneratedRecipeResponse.StepDto> parseSteps(JsonNode instructionNode) {
        if (instructionNode == null || instructionNode.isNull()) {
            return List.of();
        }
        List<GeneratedRecipeResponse.StepDto> steps = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(1);
        collectSteps(instructionNode, steps, counter);
        return steps;
    }

    private void collectSteps(JsonNode node, List<GeneratedRecipeResponse.StepDto> steps, AtomicInteger counter) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectSteps(child, steps, counter);
            }
            return;
        }
        if (node.isTextual()) {
            addStep(node.asText(), null, counter, steps);
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (node.has("itemListElement")) {
            collectSteps(node.get("itemListElement"), steps, counter);
            return;
        }
        String content = textOrNull(node.has("text") ? node.get("text") : node.get("description"));
        if (content == null) {
            content = textOrNull(node.get("name"));
        }
        Integer suggestedTime = parseDurationToMinutes(node.get("prepTime"));
        addStep(content, textOrNull(node.get("tip")), counter, steps, suggestedTime);
    }

    private void addStep(String content, String tip, AtomicInteger counter, List<GeneratedRecipeResponse.StepDto> steps) {
        addStep(content, tip, counter, steps, null);
    }

    private void addStep(String content, String tip, AtomicInteger counter, List<GeneratedRecipeResponse.StepDto> steps, Integer suggestedTime) {
        if (content == null || content.isBlank()) {
            return;
        }
        GeneratedRecipeResponse.StepDto dto = new GeneratedRecipeResponse.StepDto();
        dto.setStepNo(counter.getAndIncrement());
        dto.setContent(content.trim());
        dto.setTips(tip);
        dto.setSuggestedTime(suggestedTime);
        steps.add(dto);
    }

    private Integer parseDurationToMinutes(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        value = value.trim();
        try {
            Duration duration = Duration.parse(value);
            long minutes = duration.toMinutes();
            if (minutes <= 0) {
                return null;
            }
            return (int) minutes;
        } catch (Exception ignored) {
            Matcher matcher = DURATION_PATTERN.matcher(value);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ex) {
                    log.debug("Không thể parse duration '{}': {}", value, ex.getMessage());
                }
            }
            return null;
        }
    }

    private void fillFallbacksIfNeeded(GeneratedRecipeResponse response, Document doc) {
        if (response.getTitle() == null || response.getTitle().isBlank()) {
            response.setTitle(doc.title());
        }
        if ((response.getDescription() == null || response.getDescription().isBlank())) {
            Element meta = doc.selectFirst("meta[property=og:description]");
            response.setDescription(meta != null ? meta.attr("content") : null);
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
