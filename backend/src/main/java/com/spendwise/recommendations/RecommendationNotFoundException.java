package com.spendwise.recommendations;

import com.spendwise.common.web.ApiException;
import org.springframework.http.HttpStatus;

public class RecommendationNotFoundException extends ApiException {

    public RecommendationNotFoundException() {
        super("RECOMMENDATION_NOT_FOUND", HttpStatus.NOT_FOUND, "Recommendation not found");
    }
}
