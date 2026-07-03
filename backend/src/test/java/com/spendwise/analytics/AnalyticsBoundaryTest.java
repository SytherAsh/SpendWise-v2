package com.spendwise.analytics;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * E7-S3-T1's Required Test — makes "Analytics is strictly read-only" (CLAUDE.md; docs/architecture.md
 * "Analytics — reads from all modules (read-only), must not call any write methods on any module")
 * build-breaking. Per this epic's data-access decision, Analytics never calls any other module's
 * service/repository at all — it reads {@code transactions}/{@code transaction_categories}/
 * {@code categories} directly via its own RLS-scoped {@link AnalyticsRepository} (mirroring {@code
 * com.spendwise.transaction.TransactionRepository}'s pattern, not its class). This rule is
 * therefore stronger than "no writes": no class in {@code com.spendwise.analytics} may depend on
 * any class in another module's package at all, proving zero cross-module coupling rather than
 * just the absence of a write call.
 */
class AnalyticsBoundaryTest {

    private static final JavaClasses IMPORTED_CLASSES = new ClassFileImporter().importPackages("com.spendwise");

    private static final String[] OTHER_MODULE_PACKAGES = {
        "com.spendwise.transaction..",
        "com.spendwise.budget..",
        "com.spendwise.alerts..",
        "com.spendwise.categorization..",
        "com.spendwise.ingest..",
        "com.spendwise.recommendations..",
        "com.spendwise.chatbot..",
        "com.spendwise.admin..",
        "com.spendwise.user..",
        "com.spendwise.auth.."
    };

    @Test
    void analyticsModuleNeverDependsOnAnotherModule() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("com.spendwise.analytics..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(OTHER_MODULE_PACKAGES);

        rule.check(IMPORTED_CLASSES);
    }
}
