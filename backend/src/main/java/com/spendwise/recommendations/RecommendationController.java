package com.spendwise.recommendations;

import com.spendwise.recommendations.dto.RecommendationResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** docs/api.md "/recommendations" — owned by the Recommendations module. */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationsService recommendationsService;

    public RecommendationController(RecommendationsService recommendationsService) {
        this.recommendationsService = recommendationsService;
    }

    @GetMapping
    public List<RecommendationResponse> list(@AuthenticationPrincipal UUID userId) {
        return recommendationsService.listActive(userId).stream().map(RecommendationResponse::from).toList();
    }

    @PutMapping("/{id}/dismiss")
    public void dismiss(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        recommendationsService.dismiss(userId, id);
    }
}
