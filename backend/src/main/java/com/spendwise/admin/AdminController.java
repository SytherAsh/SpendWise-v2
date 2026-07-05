package com.spendwise.admin;

import com.spendwise.admin.dto.AdminLogResponse;
import com.spendwise.admin.dto.AdminUserDetailResponse;
import com.spendwise.admin.dto.AdminUserSummaryResponse;
import com.spendwise.analytics.dto.AnalyticsComparisonResponse;
import com.spendwise.analytics.dto.AnalyticsSummaryResponse;
import com.spendwise.categorization.dto.MlEvaluationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/** docs/api.md `/admin` — the Admin Portal (Epic 11). Every route sits behind {@code adminFilterChain} (E1-S2-T1). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public List<AdminUserSummaryResponse> listUsers() {
        return adminService.listUsers().stream().map(AdminUserSummaryResponse::from).toList();
    }

    @GetMapping("/users/{id}")
    public AdminUserDetailResponse getUserDetail(@PathVariable UUID id) {
        return AdminUserDetailResponse.from(adminService.getUserDetail(id));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /** Reuses {@link AnalyticsSummaryResponse}'s exact wire shape — the numbers are a sum, not a new shape. */
    @GetMapping("/analytics")
    public AnalyticsSummaryResponse analytics(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return AnalyticsSummaryResponse.from(adminService.getAggregateAnalytics(parseDate(from), parseDate(to)));
    }

    @GetMapping("/analytics/comparison")
    public AnalyticsComparisonResponse analyticsComparison(@RequestParam(required = false) String granularity) {
        return AnalyticsComparisonResponse.from(adminService.getAggregateComparison(granularity));
    }

    @GetMapping("/logs")
    public List<AdminLogResponse> logs(@RequestParam(required = false, name = "eventType") String eventType) {
        return adminService.getLogs(eventType).stream().map(AdminLogResponse::from).toList();
    }

    @GetMapping("/ml/accuracy")
    public MlEvaluationResponse mlAccuracy() {
        return adminService.getMlAccuracy();
    }

    @PostMapping("/ml/retrain")
    public ResponseEntity<Void> retrain() {
        adminService.triggerRetrain();
        return ResponseEntity.accepted().build();
    }

    /** Accepts both a full ISO-8601 instant and a date-only ISO-8601 string, mirroring {@code AnalyticsController}. */
    private static Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
    }
}
