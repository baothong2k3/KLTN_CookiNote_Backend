package fit.kltn_cookinote_backend.dtos.response;

import fit.kltn_cookinote_backend.entities.Recipe;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record RecipeCardResponse(
        Long id,
        String title,
        String imageUrl,
        String ownerName,
        LocalDateTime createdAt,
        String difficulty,  // String để tránh buộc client cập nhật enum
        Long view,
        boolean deleted
) {
    public static RecipeCardResponse from(Recipe r) {
        String title = r.isDeleted() ? "[ĐÃ XÓA] " + r.getTitle() : r.getTitle();
        return RecipeCardResponse.builder()
                .id(r.getId())
                .title(title)
                .imageUrl(r.getImageUrl())
                .ownerName(r.getUser() != null ? r.getUser().getDisplayName() : null)
                .createdAt(r.getCreatedAt())
                .difficulty(r.getDifficulty() != null ? r.getDifficulty().name() : null)
                .view(r.getView())
                .deleted(r.isDeleted())
                .build();
    }
}
