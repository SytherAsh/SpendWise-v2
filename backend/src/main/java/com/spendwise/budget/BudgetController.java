package com.spendwise.budget;

import com.spendwise.budget.dto.BudgetProgressResponse;
import com.spendwise.budget.dto.BudgetResponse;
import com.spendwise.budget.dto.BudgetSuggestionResponse;
import com.spendwise.budget.dto.CreateBudgetRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** docs/api.md "/budgets" — owned by the Budget module. */
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    public BudgetResponse upsert(@AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateBudgetRequest request) {
        Budget budget = budgetService.upsert(userId, request.categoryId(), request.monthlyLimit());
        return BudgetResponse.from(budget);
    }

    @GetMapping
    public List<BudgetResponse> list(@AuthenticationPrincipal UUID userId) {
        return budgetService.listForCurrentMonth(userId).stream().map(BudgetResponse::from).toList();
    }

    @GetMapping("/progress")
    public List<BudgetProgressResponse> progress(@AuthenticationPrincipal UUID userId) {
        return budgetService.progressForCurrentMonth(userId).stream().map(BudgetProgressResponse::from).toList();
    }

    @GetMapping("/suggestions")
    public List<BudgetSuggestionResponse> suggestions(@AuthenticationPrincipal UUID userId) {
        return budgetService.suggestions(userId).stream().map(BudgetSuggestionResponse::from).toList();
    }
}
