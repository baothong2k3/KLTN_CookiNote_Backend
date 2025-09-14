package fit.kltn_cookinote_backend.dtos.response;

import fit.kltn_cookinote_backend.entities.RecipeIngredient;
import lombok.Builder;

@Builder
public record RecipeIngredientItem(
        Long id,
        String name,
        String quantity
) {
    public static RecipeIngredientItem from(RecipeIngredient i) {
        return RecipeIngredientItem.builder()
                .id(i.getId())
                .name(i.getName())
                .quantity(i.getQuantity())
                .build();
    }
}
