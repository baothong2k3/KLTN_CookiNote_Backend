package fit.kltn_cookinote_backend.dtos.request;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public record RecipeStepUpdateRequest(
        String content,
        Integer stepNo,
        Integer suggestedTime,
        String tips,
        List<String> keepUrls,
        List<MultipartFile> addFiles
) {
}
