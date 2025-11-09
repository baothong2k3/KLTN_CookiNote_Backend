/*
 * @ (#) DailyMenuServiceImpl.java    1.0    09/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 09/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.DailyMenuRecipeCardResponse;
import fit.kltn_cookinote_backend.dtos.response.DailyMenuResponse;
import fit.kltn_cookinote_backend.dtos.response.DailyMenuSuggestionResponse;
import fit.kltn_cookinote_backend.entities.*;
import fit.kltn_cookinote_backend.enums.DailyMenuStrategy;
import fit.kltn_cookinote_backend.enums.MealType;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.repositories.*;
import fit.kltn_cookinote_backend.services.DailyMenuService;
import fit.kltn_cookinote_backend.services.MealTypeResolver;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DailyMenuServiceImpl implements DailyMenuService {

    private static final int DEFAULT_SIZE = 6;
    private static final int MAX_POPULARITY_PAGE_SIZE = 20;
    private static final int FRESHNESS_WINDOW_DAYS = 21;
    private static final double FAVORITE_CATEGORY_BONUS = 0.15;
    private static final double VARIETY_BONUS = 0.1;
    private static final String JUSTIFICATION_DELIMITER = ";";


    private final RecipeRepository recipeRepository;
    private final FavoriteRepository favoriteRepository;
    private final CookedHistoryRepository cookedHistoryRepository;
    private final MealTypeResolver mealTypeResolver;

    private final DailyMenuRepository dailyMenuRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public DailyMenuResponse getOrGenerateDailyMenu(Long userId, LocalDate date) {

        // --- BƯỚC 1: KIỂM TRA DB ---
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng: " + userId));

        List<DailyMenu> existingMenuCheck = dailyMenuRepository.findByUserIdAndMenuDate(userId, date);

        if (!existingMenuCheck.isEmpty()) {
            // Đã có dữ liệu, tải đầy đủ (với recipe) và trả về
            List<DailyMenu> existingMenuWithRecipes = dailyMenuRepository.findByUserAndMenuDateWithRecipe(user, date);
            return buildResponseFromSavedMenu(existingMenuWithRecipes, date);
        }

        // --- BƯỚC 2: CHƯA CÓ TRONG DB -> TẠO MỚI ---
        List<Recipe> allPublicRecipes = recipeRepository.findAllWithUserAndCategory();
        Map<Long, Recipe> recipeIndex = allPublicRecipes.stream()
                .filter(this::isVisibleRecipe)
                .collect(Collectors.toMap(Recipe::getId, Function.identity()));

        if (recipeIndex.isEmpty()) {
            return new DailyMenuResponse(null, null, "NO_DATA", null, FRESHNESS_WINDOW_DAYS, date, List.of());
        }

        // --- CẬP NHẬT LOGIC "LÀM MỚI" ---
        LocalDateTime freshnessCutoff = LocalDateTime.now().minusDays(FRESHNESS_WINDOW_DAYS);
        Set<Long> recentlyCookedIds = new HashSet<>(cookedHistoryRepository.findRecentRecipeIds(userId, freshnessCutoff));
        List<DailyMenu> yesterdayMenu = dailyMenuRepository.findByUserIdAndMenuDate(userId, date.minusDays(1));
        Set<Long> yesterdaySuggestedIds = yesterdayMenu.stream()
                .map(dm -> dm.getRecipe().getId())
                .collect(Collectors.toSet());
        Set<Long> exclusionSet = Stream.concat(recentlyCookedIds.stream(), yesterdaySuggestedIds.stream())
                .collect(Collectors.toSet());

        List<CookedHistory> cookedHistories = cookedHistoryRepository.findByUser_UserIdOrderByCookedAtDesc(userId);
        List<Favorite> favorites = favoriteRepository.findByUser_UserIdOrderByIdDesc(userId);

        Recipe anchorRecipe = selectAnchorRecipe(recipeIndex, cookedHistories, favorites);
        String anchorSource = determineAnchorSource(anchorRecipe, cookedHistories, favorites);

        if (anchorRecipe == null) {
            anchorRecipe = pickFallbackAnchor(recipeIndex.values());
            anchorSource = "POPULARITY_SEED";
        }

        Map<Long, SuggestionAccumulator> suggestionMap = new LinkedHashMap<>();

        if (anchorRecipe != null) {
            applyContentBasedStrategy(anchorRecipe, recipeIndex.values(), exclusionSet, suggestionMap);
            applyCollaborativeStrategy(userId, anchorRecipe, recipeIndex, exclusionSet, suggestionMap);
        }
        applyPopularityStrategy(recipeIndex, suggestionMap);
        Long favoriteCategoryId = resolveFavoriteCategoryId(cookedHistories, favorites);
        String favoriteCategoryName = resolveCategoryName(recipeIndex, favoriteCategoryId);
        enrichPersonalizationSignals(exclusionSet, favoriteCategoryId, suggestionMap);

        Map<MealType, List<SuggestionAccumulator>> groupedByMealType = suggestionMap.values().stream()
                .filter(acc -> acc.mealType != null)
                .collect(Collectors.groupingBy(acc -> acc.mealType));

        List<DailyMenuSuggestionResponse> suggestions = new ArrayList<>();
        List<DailyMenu> menusToSave = new ArrayList<>();
        final String finalAnchorSource = anchorSource;

        for (MealType mealType : MealType.values()) {
            Optional<SuggestionAccumulator> topSuggestion = groupedByMealType.getOrDefault(mealType, Collections.emptyList())
                    .stream()
                    .max(Comparator.comparingDouble(SuggestionAccumulator::score));

            topSuggestion.ifPresent(acc -> {
                double roundedScore = roundScore(acc.score);
                List<String> justifications = acc.justificationsView();

                suggestions.add(new DailyMenuSuggestionResponse(
                        DailyMenuRecipeCardResponse.from(acc.recipe),
                        acc.mealType,
                        roundedScore,
                        acc.strategiesView(),
                        justifications
                ));

                menusToSave.add(DailyMenu.builder()
                        .user(user)
                        .menuDate(date)
                        .mealType(acc.mealType)
                        .recipe(acc.recipe)
                        .anchorSource(finalAnchorSource)
                        .strategy(acc.strategiesView().stream()
                                .map(Enum::name)
                                .collect(Collectors.joining(",")))
                        .score(roundedScore)
                        .justifications(String.join(JUSTIFICATION_DELIMITER, justifications))
                        .build());
            });
        }

        if (!menusToSave.isEmpty()) {
            dailyMenuRepository.saveAll(menusToSave);
        }

        suggestions.sort(Comparator.comparing(DailyMenuSuggestionResponse::mealType));
        DailyMenuRecipeCardResponse anchorCard = anchorRecipe != null ? DailyMenuRecipeCardResponse.from(anchorRecipe) : null;
        MealType anchorMealType = anchorRecipe != null ? mealTypeResolver.resolveMealType(anchorRecipe) : null;

        return new DailyMenuResponse(
                anchorCard,
                anchorMealType,
                anchorSource,
                favoriteCategoryName,
                FRESHNESS_WINDOW_DAYS,
                date,
                suggestions
        );
    }

    /**
     * Xây dựng DailyMenuResponse từ danh sách DailyMenu đã lưu trong DB.
     */
    private DailyMenuResponse buildResponseFromSavedMenu(List<DailyMenu> savedMenus, LocalDate date) {
        if (savedMenus.isEmpty()) {
            return new DailyMenuResponse(null, null, "NO_DATA", null, FRESHNESS_WINDOW_DAYS, date, List.of());
        }

        List<DailyMenuSuggestionResponse> suggestions = new ArrayList<>();
        String anchorSource = "UNKNOWN";

        for (DailyMenu dm : savedMenus) {
            if (dm.getRecipe() == null) continue;

            anchorSource = dm.getAnchorSource();

            // Tái tạo lại danh sách chiến lược từ chuỗi đã lưu
            List<DailyMenuStrategy> strategies = (StringUtils.hasText(dm.getStrategy()))
                    ? Arrays.stream(dm.getStrategy().split(","))
                    .map(s -> {
                        try {
                            return DailyMenuStrategy.valueOf(s);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList()
                    : List.of();

            // --- ĐỌC DỮ LIỆU MỚI TỪ DB ---
            // Tái tạo lại danh sách lý do
            List<String> justifications = (StringUtils.hasText(dm.getJustifications()))
                    ? Arrays.stream(dm.getJustifications().split(JUSTIFICATION_DELIMITER))
                    .toList()
                    : List.of();

            // Lấy điểm số đã lưu
            double score = (dm.getScore() != null) ? dm.getScore() : 0.0;

            suggestions.add(new DailyMenuSuggestionResponse(
                    DailyMenuRecipeCardResponse.from(dm.getRecipe()),
                    dm.getMealType(),
                    score,
                    strategies,
                    justifications
            ));
        }

        suggestions.sort(Comparator.comparing(DailyMenuSuggestionResponse::mealType));

        return new DailyMenuResponse(
                null,
                null,
                anchorSource,
                null,
                FRESHNESS_WINDOW_DAYS,
                date,
                suggestions
        );
    }

    // --- CHIẾN LƯỢC GỢI Ý ---
    private void applyContentBasedStrategy(Recipe anchor,
                                           Collection<Recipe> candidates,
                                           Set<Long> recentlyCooked,
                                           Map<Long, SuggestionAccumulator> suggestionMap) {
        Long anchorId = anchor.getId();
        Long anchorCategoryId = anchor.getCategory() != null ? anchor.getCategory().getId() : null;
        String anchorTitle = anchor.getTitle();
        List<RecipeIngredient> anchorIngredientList = Optional.ofNullable(anchor.getIngredients())
                .orElseGet(Collections::emptyList);
        Set<String> anchorIngredients = anchorIngredientList.stream()
                .map(RecipeIngredient::getName)
                .filter(Objects::nonNull)
                .map(this::normalize)
                .collect(Collectors.toSet());

        for (Recipe candidate : candidates) {
            if (candidate.getId().equals(anchorId) || recentlyCooked.contains(candidate.getId())) {
                continue;
            }
            double score = 0.0;
            List<String> reasons = new ArrayList<>();

            Long candidateCategoryId = candidate.getCategory() != null ? candidate.getCategory().getId() : null;
            if (anchorCategoryId != null && Objects.equals(anchorCategoryId, candidateCategoryId)) {
                score += 0.4;
                reasons.add("Cùng danh mục với món vừa xem/nấu");
            }

            if (anchor.getDifficulty() != null && anchor.getDifficulty().equals(candidate.getDifficulty())) {
                score += 0.2;
                reasons.add("Độ khó tương tự");
            }

            List<RecipeIngredient> candidateIngredientList = Optional.ofNullable(candidate.getIngredients())
                    .orElseGet(Collections::emptyList);
            if (!anchorIngredients.isEmpty() && !candidateIngredientList.isEmpty()) {
                Set<String> candidateIngredients = candidateIngredientList.stream()
                        .map(RecipeIngredient::getName)
                        .filter(Objects::nonNull)
                        .map(this::normalize)
                        .collect(Collectors.toSet());
                if (!candidateIngredients.isEmpty()) {
                    Set<String> intersection = new HashSet<>(anchorIngredients);
                    intersection.retainAll(candidateIngredients);
                    double ingredientScore = (double) intersection.size() / (double) anchorIngredients.size();
                    if (ingredientScore > 0) {
                        score += Math.min(ingredientScore, 1.0) * 0.4;
                        reasons.add("Chia sẻ " + intersection.size() + " nguyên liệu với món bạn quan tâm");
                    }
                }
            }

            if (score > 0) {
                String summaryReason = String.format("Gợi ý tương tự \"%s\"", anchorTitle);
                reasons.add(0, summaryReason);
                mergeSuggestion(candidate, score, DailyMenuStrategy.CONTENT_BASED, reasons, suggestionMap);
            }
        }
    }

    private void applyCollaborativeStrategy(Long userId,
                                            Recipe anchor,
                                            Map<Long, Recipe> recipeIndex,
                                            Set<Long> recentlyCooked,
                                            Map<Long, SuggestionAccumulator> suggestionMap) {
        List<Favorite> anchorFavorites = favoriteRepository.findByRecipe_Id(anchor.getId());
        Set<Long> similarUserIds = anchorFavorites.stream()
                .map(fav -> fav.getUser() != null ? fav.getUser().getUserId() : null)
                .filter(Objects::nonNull)
                .filter(id -> !Objects.equals(id, userId))
                .collect(Collectors.toSet());

        if (similarUserIds.isEmpty()) {
            return;
        }

        List<Long> collaborativeRecipeIds = favoriteRepository.findActiveRecipeIdsByUserIds(similarUserIds);
        if (collaborativeRecipeIds.isEmpty()) {
            return;
        }

        Map<Long, Long> frequency = collaborativeRecipeIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !Objects.equals(id, anchor.getId()))
                .filter(id -> !recentlyCooked.contains(id))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        for (Map.Entry<Long, Long> entry : frequency.entrySet()) {
            Recipe recipe = recipeIndex.get(entry.getKey());
            if (recipe == null) {
                continue;
            }
            long count = entry.getValue();
            double score = 0.5 + Math.min(count, 5) * 0.1;
            String reason = String.format("%d người cũng thích món này sau khi lưu \"%s\"", count, anchor.getTitle());
            mergeSuggestion(recipe, score, DailyMenuStrategy.COLLABORATIVE, List.of(reason), suggestionMap);
        }
    }

    private void applyPopularityStrategy(Map<Long, Recipe> recipeIndex,
                                         Map<Long, SuggestionAccumulator> suggestionMap) {
        int fetchSize = Math.min(Math.max(suggestionMap.size(), DEFAULT_SIZE) * 2, MAX_POPULARITY_PAGE_SIZE);
        recipeRepository.findByPrivacyAndDeletedFalseOrderByViewDesc(Privacy.PUBLIC, PageRequest.of(0, fetchSize))
                .forEach(recipe -> {
                    Recipe indexed = recipeIndex.get(recipe.getId());
                    if (indexed != null) {
                        double rankScore = 0.3;
                        mergeSuggestion(indexed, rankScore, DailyMenuStrategy.POPULARITY,
                                List.of("Đang được nhiều người xem và đánh giá cao"), suggestionMap);
                    }
                });
    }

    private void enrichPersonalizationSignals(Set<Long> exclusionSet, // <-- Đổi tên biến
                                              Long favoriteCategoryId,
                                              Map<Long, SuggestionAccumulator> suggestionMap) {
        for (SuggestionAccumulator accumulator : suggestionMap.values()) {
            if (accumulator.mealType == null) {
                accumulator.mealType = mealTypeResolver.resolveMealType(accumulator.recipe);
            }

            // Sử dụng exclusionSet (đã bao gồm cả món đã nấu VÀ món đã gợi ý hôm qua)
            if (!exclusionSet.contains(accumulator.recipe.getId())) {
                accumulator.score += VARIETY_BONUS;
                accumulator.strategies.add(DailyMenuStrategy.PERSONALIZED_VARIETY);
                // Cập nhật lại lý do cho rõ ràng hơn
                accumulator.justifications.add(String.format("Mới! (Chưa nấu/xem trong %d ngày)", FRESHNESS_WINDOW_DAYS));
            }
            if (favoriteCategoryId != null && accumulator.recipe.getCategory() != null
                    && Objects.equals(favoriteCategoryId, accumulator.recipe.getCategory().getId())) {
                accumulator.score += FAVORITE_CATEGORY_BONUS;
                accumulator.strategies.add(DailyMenuStrategy.PERSONALIZED_FAVORITE);
                accumulator.justifications.add("Thuộc nhóm món bạn thường yêu thích hoặc nấu");
            }
        }
    }

    private Recipe selectAnchorRecipe(Map<Long, Recipe> recipeIndex,
                                      List<CookedHistory> histories,
                                      List<Favorite> favorites) {
        for (CookedHistory history : histories) {
            Recipe recipe = history.getRecipe();
            if (recipe != null && recipeIndex.containsKey(recipe.getId())) {
                return recipeIndex.get(recipe.getId());
            }
        }
        for (Favorite favorite : favorites) {
            Recipe recipe = favorite.getRecipe();
            if (recipe != null && recipeIndex.containsKey(recipe.getId())) {
                return recipeIndex.get(recipe.getId());
            }
        }
        return null;
    }

    private String determineAnchorSource(Recipe anchorRecipe,
                                         List<CookedHistory> histories,
                                         List<Favorite> favorites) {
        if (anchorRecipe == null) {
            return "NONE";
        }
        Optional<CookedHistory> fromHistory = histories.stream()
                .filter(history -> history.getRecipe() != null)
                .filter(history -> Objects.equals(history.getRecipe().getId(), anchorRecipe.getId()))
                .findFirst();
        if (fromHistory.isPresent()) {
            return "COOKED_HISTORY";
        }
        Optional<Favorite> fromFavorite = favorites.stream()
                .filter(favorite -> favorite.getRecipe() != null)
                .filter(favorite -> Objects.equals(favorite.getRecipe().getId(), anchorRecipe.getId()))
                .findFirst();
        if (fromFavorite.isPresent()) {
            return "FAVORITE";
        }
        return "UNKNOWN";
    }

    private Recipe pickFallbackAnchor(Collection<Recipe> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            return null;
        }
        return recipes.stream()
                .filter(Objects::nonNull)
                .min(Comparator.comparing(Recipe::getView, Comparator.nullsLast(Comparator.reverseOrder())))
                .orElse(null);
    }

    private Long resolveFavoriteCategoryId(List<CookedHistory> histories, List<Favorite> favorites) {
        Map<Long, Long> counter = new HashMap<>();
        histories.stream()
                .map(CookedHistory::getRecipe)
                .filter(Objects::nonNull)
                .map(Recipe::getCategory)
                .filter(Objects::nonNull)
                .forEach(category -> counter.merge(category.getId(), 2L, Long::sum));
        favorites.stream()
                .map(Favorite::getRecipe)
                .filter(Objects::nonNull)
                .map(Recipe::getCategory)
                .filter(Objects::nonNull)
                .forEach(category -> counter.merge(category.getId(), 1L, Long::sum));
        return counter.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String resolveCategoryName(Map<Long, Recipe> recipeIndex, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return recipeIndex.values().stream()
                .map(Recipe::getCategory)
                .filter(Objects::nonNull)
                .filter(category -> Objects.equals(categoryId, category.getId()))
                .map(category -> category.getName() != null ? category.getName() : null)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean isVisibleRecipe(Recipe recipe) {
        return recipe != null && !recipe.isDeleted() && recipe.getPrivacy() == Privacy.PUBLIC;
    }

    private String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private void mergeSuggestion(Recipe recipe,
                                 double additionalScore,
                                 DailyMenuStrategy strategy,
                                 List<String> reasons,
                                 Map<Long, SuggestionAccumulator> suggestionMap) {
        SuggestionAccumulator accumulator = suggestionMap.computeIfAbsent(recipe.getId(), id -> new SuggestionAccumulator(recipe));
        accumulator.score += additionalScore;
        accumulator.strategies.add(strategy);
        for (String reason : reasons) {
            if (reason != null && !reason.isBlank()) {
                accumulator.justifications.add(reason);
            }
        }
    }

    private double roundScore(double score) {
        return Math.round(score * 100.0) / 100.0;
    }

    private static class SuggestionAccumulator {
        private final Recipe recipe;
        private double score;
        private MealType mealType; // Đã thêm mealType ở đây
        private final LinkedHashSet<DailyMenuStrategy> strategies = new LinkedHashSet<>();
        private final LinkedHashSet<String> justifications = new LinkedHashSet<>();

        private SuggestionAccumulator(Recipe recipe) {
            this.recipe = recipe;
        }

        private double score() {
            return score;
        }

        private List<DailyMenuStrategy> strategiesView() {
            return new ArrayList<>(strategies);
        }

        private List<String> justificationsView() {
            return new ArrayList<>(justifications);
        }
    }
}