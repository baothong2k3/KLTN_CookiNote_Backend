/*
 * @ (#) RecipeImportServiceImpl.java    1.0    20/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/11/2025
 * @version: 1.2
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
import java.util.*;
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

    // Pattern bắt định lượng ở cuối câu: "Tên món" + " " + "Số lượng" + "Đơn vị"
    // Group 1: Tên (lazy match)
    // Group 2: Số lượng (chấp nhận phẩy, chấm, gạch chéo)
    // Group 3: Đơn vị
    private static final Pattern END_QUANTITY_PATTERN = Pattern.compile(
            "^(.*?)\\s+(\\d+(?:[.,/]\\d+)?)\\s*(mg|g|kg|ml|l|gram|gam|lít|trái|quả|củ|muỗng|thìa|bát|chén|hộp|gói|lon|chai|con|cây|bó|tép|nhánh|lát|miếng|m|mm|cm|tô|khứa|giọt|túi|viên)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private final ObjectMapper objectMapper;

    @Override
    public GeneratedRecipeResponse importFromUrl(ImportRecipeRequest request) {
        String normalizedUrl = normalizeUrl(request.url());
        validateSupportedHost(normalizedUrl);

        Document document = fetchDocument(normalizedUrl);

        // 1. Lấy tất cả các node JSON-LD để tìm dữ liệu phân tán
        List<JsonNode> allJsonNodes = extractAllJsonLdNodes(document);

        // 2. Tìm node chứa Recipe chính
        JsonNode recipeNode = findRecipeNode(allJsonNodes);
        if (recipeNode == null) {
            throw new IllegalStateException("Không tìm thấy dữ liệu công thức trong trang đã cung cấp.");
        }

        // 3. Map dữ liệu cơ bản
        GeneratedRecipeResponse response = mapToResponse(recipeNode);

        // 4. Xử lý Difficulty: Nếu chưa có, tìm trong các node khác (vd: educationalLevel)
        if (response.getDifficulty() == null) {
            response.setDifficulty(findDifficultyInAllNodes(allJsonNodes));
        }

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

    /**
     * Trích xuất tất cả các node JSON-LD có trong trang
     */
    private List<JsonNode> extractAllJsonLdNodes(Document document) {
        List<JsonNode> nodes = new ArrayList<>();
        Elements scripts = document.select("script[type=application/ld+json]");
        for (Element script : scripts) {
            String json = script.data();
            if (json.isBlank()) continue;
            try {
                JsonNode root = objectMapper.readTree(json);
                // Nếu root là mảng, add từng phần tử
                if (root.isArray()) {
                    for (JsonNode child : root) nodes.add(child);
                } else {
                    nodes.add(root);
                }
                // Nếu có @graph, add con của graph
                if (root.has("@graph")) {
                    JsonNode graph = root.get("@graph");
                    if (graph.isArray()) {
                        for (JsonNode gNode : graph) nodes.add(gNode);
                    }
                }
            } catch (Exception ex) {
                log.debug("Không thể parse JSON-LD: {}", ex.getMessage());
            }
        }
        return nodes;
    }

    /**
     * Tìm node có @type là Recipe trong danh sách
     */
    private JsonNode findRecipeNode(List<JsonNode> nodes) {
        for (JsonNode node : nodes) {
            if (isType(node, "Recipe")) return node;
            // Kiểm tra lồng nhau nếu cần (như mainEntity)
            if (node.has("mainEntity")) {
                JsonNode main = node.get("mainEntity");
                if (isType(main, "Recipe")) return main;
            }
        }
        return null;
    }

    private boolean isType(JsonNode node, String type) {
        if (!node.has("@type")) return false;
        JsonNode typeNode = node.get("@type");
        if (typeNode.isTextual()) return type.equalsIgnoreCase(typeNode.asText());
        if (typeNode.isArray()) {
            for (JsonNode t : typeNode) {
                if (t.isTextual() && type.equalsIgnoreCase(t.asText())) return true;
            }
        }
        return false;
    }

    /**
     * Tìm Difficulty từ bất kỳ node nào có chứa thông tin (educationalLevel hoặc recipeCategory)
     */
    private Difficulty findDifficultyInAllNodes(List<JsonNode> nodes) {
        for (JsonNode node : nodes) {
            // Ưu tiên educationalLevel (thường thấy ở LearningResource/VideoObject trên monngonmoingay)
            if (node.has("educationalLevel")) {
                Difficulty d = parseDifficultyString(textOrNull(node.get("educationalLevel")));
                if (d != null) return d;
            }
            // Check recipeCategory nếu có ở các node khác
            if (node.has("recipeCategory")) {
                Difficulty d = parseDifficulty(node.get("recipeCategory"));
                if (d != null) return d;
            }
        }
        return null;
    }

    private GeneratedRecipeResponse mapToResponse(JsonNode recipeNode) {
        GeneratedRecipeResponse response = new GeneratedRecipeResponse();
        response.setTitle(textOrNull(recipeNode.get("name")));
        response.setDescription(textOrNull(recipeNode.get("description")));
        response.setPrepareTime(parseDurationToMinutes(recipeNode.get("prepTime")));
        response.setCookTime(parseDurationToMinutes(recipeNode.get("cookTime")));

        // Thử lấy difficulty từ node recipe chính
        response.setDifficulty(parseDifficulty(recipeNode.get("recipeCategory")));

        response.setIngredients(parseIngredients(recipeNode.get("recipeIngredient")));
        response.setSteps(parseSteps(recipeNode.get("recipeInstructions")));

        if (response.getIngredients() == null) response.setIngredients(List.of());
        if (response.getSteps() == null) response.setSteps(List.of());

        return response;
    }

    private Difficulty parseDifficulty(JsonNode categoryNode) {
        if (categoryNode == null || categoryNode.isNull()) return null;

        List<String> values = new ArrayList<>();
        if (categoryNode.isArray()) {
            for (JsonNode n : categoryNode) {
                String s = textOrNull(n);
                if (s != null) values.add(s);
            }
        } else {
            String s = textOrNull(categoryNode);
            if (s != null) values.add(s);
        }

        for (String val : values) {
            Difficulty d = parseDifficultyString(val);
            if (d != null) return d;
        }
        return null;
    }

    private Difficulty parseDifficultyString(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("dễ") || normalized.equals("de") || normalized.contains("de dang")) return Difficulty.EASY;
        if (normalized.contains("khó") || normalized.contains("kho")) return Difficulty.HARD;
        if (normalized.contains("trung bình") || normalized.contains("vừa")) return Difficulty.MEDIUM;
        return null;
    }

    private List<GeneratedRecipeResponse.IngredientDto> parseIngredients(JsonNode ingredientNode) {
        if (ingredientNode == null || ingredientNode.isNull()) return List.of();

        List<GeneratedRecipeResponse.IngredientDto> result = new ArrayList<>();
        if (ingredientNode.isArray()) {
            for (JsonNode item : ingredientNode) {
                result.add(buildIngredient(textOrNull(item)));
            }
        } else {
            result.add(buildIngredient(textOrNull(ingredientNode)));
        }

        // Lọc bỏ các null
        result.removeIf(Objects::isNull);
        return result;
    }

    /**
     * Cập nhật: Sử dụng Regex để bắt định lượng ở cuối chuỗi tốt hơn.
     * Xử lý clean string kỹ hơn để loại bỏ ký tự lạ (non-breaking space).
     */
    private GeneratedRecipeResponse.IngredientDto buildIngredient(String raw) {
        if (raw == null) return null;

        // Clean string: thay thế mọi loại khoảng trắng (kể cả NBSP \u00A0) bằng space thường
        String cleaned = raw.replaceAll("[\\s\\u00A0]+", " ")
                .replaceFirst("^[\\-•]+\\s*", "")
                .trim();

        if (cleaned.isEmpty()) return null;

        GeneratedRecipeResponse.IngredientDto dto = new GeneratedRecipeResponse.IngredientDto();
        String quantity = null;
        String name = cleaned;

        // 1. Thử tách bằng Regex (Định lượng nằm cuối: "Cánh gà 400g")
        Matcher matcher = END_QUANTITY_PATTERN.matcher(cleaned);
        if (matcher.matches()) {
            name = matcher.group(1).trim();
            String qtyNum = matcher.group(2);
            String qtyUnit = matcher.group(3);
            quantity = qtyNum + " " + qtyUnit;
        }
        // 2. Nếu không khớp regex, thử tách bằng ký tự đặc biệt (:, -, –) như cũ
        else {
            int separatorIdx = findSeparatorIndex(cleaned);
            if (separatorIdx > 0) {
                // Giả sử format: "Định lượng - Tên" hoặc "Tên - Định lượng"
                // Nhưng thường web monngonmoingay format lộn xộn, ưu tiên regex trên hơn.
                // Ở đây giữ logic cũ: phần đầu là quantity nếu format "100g thịt"
                String p1 = cleaned.substring(0, separatorIdx).trim();
                String p2 = cleaned.substring(separatorIdx + 1).trim();
                // Heuristic đơn giản: cái nào bắt đầu bằng số thì là quantity
                if (Character.isDigit(p1.charAt(0))) {
                    quantity = p1;
                    name = p2;
                } else {
                    name = p1;
                    quantity = p2;
                }
            } else if (Character.isDigit(cleaned.charAt(0))) {
                // Fallback: Bắt đầu bằng số -> tách ở khoảng trắng đầu tiên (VD: "1/2 trái chanh")
                int firstSpace = cleaned.indexOf(' ');
                if (firstSpace > 0) {
                    quantity = cleaned.substring(0, firstSpace).trim();
                    name = cleaned.substring(firstSpace + 1).trim();
                }
            }
        }

        dto.setName(name);
        dto.setQuantity(quantity);
        return dto;
    }

    private int findSeparatorIndex(String text) {
        int colon = text.indexOf(':');
        if (colon > 0) return colon;
        // Bỏ qua '-' nếu regex trên đã xử lý, nhưng giữ lại làm fallback cho các trường hợp khác
        int dash = text.indexOf(" - "); // Chỉ tách nếu có khoảng trắng quanh dấu gạch
        if (dash > 0) return dash;
        return -1;
    }

    private List<GeneratedRecipeResponse.StepDto> parseSteps(JsonNode instructionNode) {
        if (instructionNode == null || instructionNode.isNull()) return List.of();

        List<GeneratedRecipeResponse.StepDto> steps = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(1);
        collectSteps(instructionNode, steps, counter);
        return steps;
    }

    private void collectSteps(JsonNode node, List<GeneratedRecipeResponse.StepDto> steps, AtomicInteger counter) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) {
            for (JsonNode child : node) collectSteps(child, steps, counter);
            return;
        }
        if (node.isTextual()) {
            addStep(node.asText(), null, counter, steps, null);
            return;
        }
        if (!node.isObject()) return;

        if (node.has("itemListElement")) {
            collectSteps(node.get("itemListElement"), steps, counter);
            return;
        }
        String content = textOrNull(node.has("text") ? node.get("text") : node.get("description"));
        if (content == null) content = textOrNull(node.get("name"));
        Integer suggestedTime = parseDurationToMinutes(node.get("prepTime")); // Có thể là cookTime hoặc duration

        addStep(content, textOrNull(node.get("tip")), counter, steps, suggestedTime);
    }

    private void addStep(String content, String tip, AtomicInteger counter, List<GeneratedRecipeResponse.StepDto> steps, Integer suggestedTime) {
        if (content == null || content.isBlank()) return;
        GeneratedRecipeResponse.StepDto dto = new GeneratedRecipeResponse.StepDto();
        dto.setStepNo(counter.getAndIncrement());
        dto.setContent(content.trim());
        dto.setTips(tip);
        dto.setSuggestedTime(suggestedTime);
        steps.add(dto);
    }

    private Integer parseDurationToMinutes(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String value = node.asText();
        if (value == null || value.isBlank()) return null;
        value = value.trim();
        try {
            Duration duration = Duration.parse(value);
            long minutes = duration.toMinutes();
            return minutes > 0 ? (int) minutes : null;
        } catch (Exception ignored) {
            Matcher matcher = DURATION_PATTERN.matcher(value);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ex) {
                    return null;
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
        if (node == null || node.isNull()) return null;
        String value = node.asText();
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}