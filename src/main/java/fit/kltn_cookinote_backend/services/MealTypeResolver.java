/*
 * @ (#) MealTypeResolver.java    1.0    09/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 09/11/2025
 * @version: 1.0
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.kltn_cookinote_backend.entities.Category;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.RecipeIngredient;
import fit.kltn_cookinote_backend.enums.MealType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MealTypeResolver {

    private static final String DATA_FILE = "meal-type-dataset.json";

    private final ObjectMapper objectMapper;

    private MealType defaultMealType = MealType.DINNER;
    private Map<String, MealType> categoryRules = Collections.emptyMap();
    private Map<MealType, List<String>> keywordRules = new EnumMap<>(MealType.class);
    private Map<MealType, List<String>> ingredientRules = new EnumMap<>(MealType.class);

    @PostConstruct
    public void load() {
        try (InputStream inputStream = new ClassPathResource(DATA_FILE).getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            this.defaultMealType = parseMealType(root.path("defaultMealType").asText(null)).orElse(MealType.DINNER);
            this.categoryRules = parseCategoryRules(root.path("categoryRules"));
            this.keywordRules = parseRuleMap(root.path("keywordRules"));
            this.ingredientRules = parseRuleMap(root.path("ingredientRules"));
            log.info("Loaded meal type dataset: {} category rules, {} keyword groups, {} ingredient groups",
                    categoryRules.size(), keywordRules.size(), ingredientRules.size());
        } catch (IOException e) {
            log.warn("Could not load meal type dataset {}. Fallback to defaults.", DATA_FILE, e);
            this.defaultMealType = MealType.DINNER;
            this.categoryRules = Collections.emptyMap();
            this.keywordRules = new EnumMap<>(MealType.class);
            this.ingredientRules = new EnumMap<>(MealType.class);
        }
    }

    public MealType resolveMealType(Recipe recipe) {
        if (recipe == null) {
            return defaultMealType;
        }

        // 1. Category rule
        Category category = recipe.getCategory();
        if (category != null && category.getName() != null) {
            MealType fromCategory = categoryRules.get(category.getName().trim().toLowerCase(Locale.ROOT));
            if (fromCategory != null) {
                return fromCategory;
            }
        }

        // 2. Keyword rules based on title/description
        String searchableText = buildSearchableText(recipe);
        MealType fromTitle = matchByKeyword(searchableText, keywordRules);
        if (fromTitle != null) {
            return fromTitle;
        }

        // 3. Ingredient rules
        MealType fromIngredients = matchByIngredients(recipe.getIngredients());
        if (fromIngredients != null) {
            return fromIngredients;
        }

        // 4. Heuristic fallback: breakfast for short prep time and light difficulty
        if (recipe.getDifficulty() != null && recipe.getDifficulty().name().equalsIgnoreCase("EASY")) {
            Integer prep = recipe.getPrepareTime();
            Integer cook = recipe.getCookTime();
            int total = (prep != null ? prep : 0) + (cook != null ? cook : 0);
            if (total > 0 && total <= 30) {
                return MealType.BREAKFAST;
            }
        }

        return defaultMealType;
    }

    private MealType matchByIngredients(List<RecipeIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return null;
        }
        Map<MealType, List<String>> rules = ingredientRules;
        if (rules.isEmpty()) {
            return null;
        }
        Set<String> normalized = new HashSet<>();
        for (RecipeIngredient ingredient : ingredients) {
            if (ingredient != null && ingredient.getName() != null) {
                normalized.add(normalize(ingredient.getName()));
            }
        }
        if (normalized.isEmpty()) {
            return null;
        }
        for (Map.Entry<MealType, List<String>> entry : rules.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (normalized.contains(normalize(keyword))) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private MealType matchByKeyword(String text, Map<MealType, List<String>> rules) {
        if (text.isBlank() || rules.isEmpty()) {
            return null;
        }
        for (Map.Entry<MealType, List<String>> entry : rules.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (!keyword.isBlank() && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private String buildSearchableText(Recipe recipe) {
        StringBuilder builder = new StringBuilder();
        if (recipe.getTitle() != null) {
            builder.append(recipe.getTitle().toLowerCase(Locale.ROOT)).append(' ');
        }
        if (recipe.getDescription() != null) {
            builder.append(recipe.getDescription().toLowerCase(Locale.ROOT)).append(' ');
        }
        if (recipe.getCategory() != null && recipe.getCategory().getName() != null) {
            builder.append(recipe.getCategory().getName().toLowerCase(Locale.ROOT)).append(' ');
        }
        return builder.toString();
    }

    private Map<String, MealType> parseCategoryRules(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isObject()) {
            return Collections.emptyMap();
        }
        Map<String, MealType> rules = new HashMap<>();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            JsonNode value = node.get(key);
            if (value != null && value.isTextual()) {
                parseMealType(value.asText())
                        .ifPresent(type -> rules.put(key.trim().toLowerCase(Locale.ROOT), type));
            }
        }
        return Collections.unmodifiableMap(rules);
    }

    private Map<MealType, List<String>> parseRuleMap(JsonNode node) {
        Map<MealType, List<String>> result = new EnumMap<>(MealType.class);
        if (node == null || node.isMissingNode() || !node.isObject()) {
            return result;
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            Optional<MealType> mealType = parseMealType(key);
            if (mealType.isPresent()) {
                JsonNode value = node.get(key);
                if (value != null && value.isArray()) {
                    List<String> keywords = new ArrayList<>();
                    value.forEach(keywordNode -> {
                        if (keywordNode.isTextual()) {
                            keywords.add(keywordNode.asText().trim().toLowerCase(Locale.ROOT));
                        }
                    });
                    result.put(mealType.get(), Collections.unmodifiableList(keywords));
                }
            }
        }
        return result;
    }


    private Optional<MealType> parseMealType(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MealType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown meal type '{}' in dataset {}", raw, DATA_FILE);
            return Optional.empty();
        }
    }

    private String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }
}
