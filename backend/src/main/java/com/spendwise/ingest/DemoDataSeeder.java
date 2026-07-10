package com.spendwise.ingest;

import com.spendwise.alerts.AlertPriority;
import com.spendwise.alerts.AlertType;
import com.spendwise.alerts.AlertsService;
import com.spendwise.budget.BudgetService;
import com.spendwise.common.demo.DemoUserRegistry;
import com.spendwise.ingest.dto.IngestTransactionItem;
import com.spendwise.recommendations.RecommendationPriority;
import com.spendwise.recommendations.RecommendationsService;
import com.spendwise.transaction.EmiService;
import com.spendwise.transaction.Transaction;
import com.spendwise.transaction.TransactionPage;
import com.spendwise.transaction.TransactionService;
import com.spendwise.transaction.util.CsvTransactionParser;
import com.spendwise.user.Contact;
import com.spendwise.user.ContactService;
import com.spendwise.user.User;
import com.spendwise.user.UserAccountService;
import com.spendwise.user.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Seeds the demo account with realistic transaction data on application startup.
 *
 * <p>Reuses the same services the real ingest/budget/contact flows use — {@link IngestService}
 * (persistence + ML categorization trigger), {@link BudgetService}, {@link ContactService} — so
 * demo transactions go through the identical pipeline a real device's synced SMS batch would,
 * per CLAUDE.md "reuse, don't rewrite".
 *
 * <p>Idempotent: skips seeding if the demo user (by phone) already exists.
 */
@Component
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserAccountService userAccountService;
    private final UserProfileService userProfileService;
    private final IngestService ingestService;
    private final TransactionService transactionService;
    private final BudgetService budgetService;
    private final ContactService contactService;
    private final EmiService emiService;
    private final AlertsService alertsService;
    private final RecommendationsService recommendationsService;
    private final CsvTransactionParser csvParser;
    private final DemoUserRegistry demoUserRegistry;

    @Value("${demo.enabled:true}")
    private boolean demoEnabled;

    @Value("${demo.phone:+919876543210}")
    private String demoPhone;

    @Value("${demo.email:demo@spendwise.local}")
    private String demoEmail;

    private static final String DEMO_CSV_PATH = "data/demo-transactions.csv";

    // category_id per db/migration/V2__transactions_and_categories.sql
    private static final int CATEGORY_SHOPPING = 1;
    private static final int CATEGORY_TRAVEL = 5;
    private static final int CATEGORY_FOOD = 7;
    private static final int CATEGORY_TRANSFERS = 10;

    public DemoDataSeeder(
            UserAccountService userAccountService,
            UserProfileService userProfileService,
            IngestService ingestService,
            TransactionService transactionService,
            BudgetService budgetService,
            ContactService contactService,
            EmiService emiService,
            AlertsService alertsService,
            RecommendationsService recommendationsService,
            CsvTransactionParser csvParser,
            DemoUserRegistry demoUserRegistry) {
        this.userAccountService = userAccountService;
        this.userProfileService = userProfileService;
        this.ingestService = ingestService;
        this.transactionService = transactionService;
        this.budgetService = budgetService;
        this.contactService = contactService;
        this.emiService = emiService;
        this.alertsService = alertsService;
        this.recommendationsService = recommendationsService;
        this.csvParser = csvParser;
        this.demoUserRegistry = demoUserRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDemoDataOnStartup() {
        if (!demoEnabled) {
            log.info("Demo data seeding is disabled (demo.enabled=false)");
            return;
        }

        // DemoUserRegistry is a fresh, empty bean on every process start (it isn't persisted),
        // so it must be re-populated here every time — including the already-seeded branch —
        // not just the first time this ever runs.
        Optional<User> existing = userAccountService.findByPhone(demoPhone);
        if (existing.isPresent()) {
            demoUserRegistry.register(existing.get().id());
            log.info("Demo user already exists, skipping seeding");
            return;
        }

        try {
            log.info("Starting demo data seeding...");
            User demoUser = createDemoUser();
            demoUserRegistry.register(demoUser.id());
            Map<String, UUID> byClientTransactionId = seedTransactions(demoUser.id());
            seedBudgets(demoUser.id());
            seedContacts(demoUser.id());
            seedEmis(demoUser.id());
            seedAlerts(demoUser.id(), byClientTransactionId);
            seedRecommendations(demoUser.id());
            log.info("Demo data seeding completed successfully");
        } catch (Exception e) {
            // Seeding is best-effort — a failure here must not prevent the app from starting.
            log.error("Failed to seed demo data", e);
        }
    }

    private User createDemoUser() {
        log.info("Creating demo user with phone: {}", demoPhone);
        User user = userAccountService.findOrCreateByPhone(demoPhone);
        userProfileService.updateEmail(user.id(), demoEmail);
        log.info("Demo user created: {}", user.id());
        return user;
    }

    /** @return every seeded transaction's client {@code transaction_id} mapped to its generated DB UUID. */
    private Map<String, UUID> seedTransactions(UUID userId) {
        log.info("Loading demo transactions from CSV: {}", DEMO_CSV_PATH);
        try {
            List<IngestTransactionItem> items;
            try (InputStream inputStream = new ClassPathResource(DEMO_CSV_PATH).getInputStream()) {
                items = csvParser.parse(inputStream);
            }
            log.info("Parsed {} transactions from CSV", items.size());

            // Same path a real device batch takes: persist + trigger ML categorization per item.
            IngestOutcome outcome = ingestService.ingestBatch(userId, items);
            log.info("Ingested demo transactions: status={}, items={}", outcome.status(), outcome.body().results().size());

            Map<String, UUID> byClientTransactionId = resolveTransactionIds(userId);
            applyCuratedCategories(userId, byClientTransactionId);
            return byClientTransactionId;
        } catch (Exception e) {
            throw new RuntimeException("Demo transaction seeding failed", e);
        }
    }

    /** Every seeded transaction's client {@code transaction_id} mapped to its generated DB UUID. */
    private Map<String, UUID> resolveTransactionIds(UUID userId) {
        Map<String, UUID> byClientTransactionId = new HashMap<>();
        UUID cursor = null;
        do {
            TransactionPage page = transactionService.list(userId, 100, cursor, null, false, null, null, null);
            for (Transaction t : page.data()) {
                byClientTransactionId.put(t.transactionId(), t.id());
            }
            cursor = page.hasMore() ? page.nextCursor() : null;
        } while (cursor != null);
        return byClientTransactionId;
    }

    /**
     * Live {@code /predict} inference can under-discriminate on demo data (a single dominant
     * class at low confidence), which would undercut a demo meant to showcase categorization
     * across the full category set. Overlays the CSV's curated {@code category} column onto the
     * just-ingested transactions using the same {@link TransactionService#correctCategory}
     * path a real user takes to fix a wrong category — no bypass of the domain model, and the
     * correction is recorded in {@code ml_corrections} like any other, same as production.
     */
    private void applyCuratedCategories(UUID userId, Map<String, UUID> byClientTransactionId) throws Exception {
        Map<String, Integer> overrides;
        try (InputStream inputStream = new ClassPathResource(DEMO_CSV_PATH).getInputStream()) {
            overrides = csvParser.parseCategoryOverrides(inputStream);
        }
        if (overrides.isEmpty()) {
            return;
        }

        int applied = 0;
        for (Map.Entry<String, Integer> entry : overrides.entrySet()) {
            UUID txnUuid = byClientTransactionId.get(entry.getKey());
            if (txnUuid == null) {
                continue;
            }
            try {
                transactionService.correctCategory(userId, txnUuid, entry.getValue());
                applied++;
            } catch (Exception e) {
                log.warn("Failed to apply curated category for transaction {}: {}", entry.getKey(), e.getMessage());
            }
        }
        log.info("Applied {} curated categories from CSV", applied);
    }

    private void seedBudgets(UUID userId) {
        log.info("Setting up demo user budgets");

        seedBudget(userId, CATEGORY_FOOD, "10000");
        seedBudget(userId, CATEGORY_TRAVEL, "7000");
        seedBudget(userId, CATEGORY_TRANSFERS, "10000");
        seedBudget(userId, CATEGORY_SHOPPING, "5000");
    }

    private void seedBudget(UUID userId, int categoryId, String limit) {
        try {
            budgetService.upsert(userId, categoryId, new BigDecimal(limit));
            log.info("Set budget for category {} to Rs.{}", categoryId, limit);
        } catch (Exception e) {
            log.warn("Failed to set budget for category {}: {}", categoryId, e.getMessage());
        }
    }

    private void seedContacts(UUID userId) {
        log.info("Setting up demo user contacts");

        seedContact(userId, "Rahul Sharma", Contact.RelationshipType.FRIEND, "Rahul", "rahul.sharma@upi");
        seedContact(userId, "Priya Verma", Contact.RelationshipType.FAMILY, "Priya", "priya.v@okaxis");
        seedContact(userId, "Amit Kumar", Contact.RelationshipType.FRIEND, "Amit", "amit.k@ibl");
        seedContact(userId, "Shreya Patel", Contact.RelationshipType.FAMILY, "Shreya", "shreya.p@upi");
    }

    private void seedContact(
            UUID userId, String name, Contact.RelationshipType type, String namePattern, String upiId) {
        try {
            contactService.create(userId, name, type, namePattern, upiId, null);
            log.info("Created contact: {} ({})", name, type);
        } catch (Exception e) {
            log.warn("Failed to create contact {}: {}", name, e.getMessage());
        }
    }

    /**
     * The real recurring-payment detector and alert evaluator are both {@code @Scheduled} jobs
     * that key off the real wall clock (see {@code AlertEvaluatorJob}), which never lines up
     * with this static, never-re-uploaded CSV — so unlike transactions/budgets/contacts above,
     * EMIs/alerts/recommendations can't be produced by simply calling the real ingest pipeline
     * and letting the job pick them up later. Instead this seeds directly through each feature's
     * own service methods (never a raw table insert), using the CSV's actual recurring merchants
     * (Car Loan EMI, Spotify, Netflix, Electricity, Gas) so the dashboard's Alerts / Upcoming
     * EMIs / Recommendations cards are never blank.
     */
    private void seedEmis(UUID userId) {
        log.info("Setting up demo user EMIs and subscriptions");

        seedEmi(userId, "Car Loan EMI", "5000", 28);
        seedEmi(userId, "Spotify Premium", "69", 27);
        seedEmi(userId, "Netflix Subscription", "199", 1);
        seedEmi(userId, "Electricity Bill", "1650", 28);
        seedEmi(userId, "Gas Bill", "1050", 12);
    }

    private void seedEmi(UUID userId, String label, String amount, int dueDay) {
        try {
            emiService.createManual(userId, label, new BigDecimal(amount), dueDay);
            log.info("Created EMI: {} (Rs.{}, due day {})", label, amount, dueDay);
        } catch (Exception e) {
            log.warn("Failed to create EMI {}: {}", label, e.getMessage());
        }
    }

    private void seedAlerts(UUID userId, Map<String, UUID> byClientTransactionId) {
        log.info("Setting up demo user alerts");

        // CATEGORY_APPROACHING_LIMIT — Shopping (category 1).
        seedThresholdAlert(
                userId,
                AlertType.CATEGORY_APPROACHING_LIMIT,
                CATEGORY_SHOPPING,
                AlertPriority.MEDIUM,
                Map.of(
                        "category_id", CATEGORY_SHOPPING,
                        "amount_spent", new BigDecimal("4200"),
                        "monthly_limit", new BigDecimal("5000"),
                        "message", "You've used 84% of your Shopping budget (Rs.4,200 of Rs.5,000)."),
                false);

        // CATEGORY_OVERSPEND — Food / Dine Out (category 7).
        seedThresholdAlert(
                userId,
                AlertType.CATEGORY_OVERSPEND,
                CATEGORY_FOOD,
                AlertPriority.HIGH,
                Map.of(
                        "category_id", CATEGORY_FOOD,
                        "amount_spent", new BigDecimal("11200"),
                        "monthly_limit", new BigDecimal("10000"),
                        "message", "You've exceeded your Food / Dine Out budget — Rs.11,200 spent of a Rs.10,000 limit."),
                false);

        // MID_MONTH_BUDGET — already read, to show both bell-badge states in the demo.
        seedThresholdAlert(
                userId,
                AlertType.MID_MONTH_BUDGET,
                null,
                AlertPriority.HIGH,
                Map.of(
                        "total_spent", new BigDecimal("17800"),
                        "total_budget", new BigDecimal("32000"),
                        "message", "You've spent 56% of your total monthly budget with the month half over."),
                true);

        // RECURRING_PAYMENT — Spotify, using the actual most-recent seeded Spotify transaction so
        // "Confirm" (which creates a real EMI from the alert) works correctly if clicked.
        UUID spotifyTxnId = byClientTransactionId.get("txn_aa5b51577841e461");
        if (spotifyTxnId != null) {
            Map<String, Object> payload = Map.of(
                    "merchant_key", "spotify@okaxis",
                    "merchant_label", "Spotify",
                    "representative_amount", new BigDecimal("69"),
                    "representative_transaction_id", spotifyTxnId.toString(),
                    "transaction_count", 12,
                    "message", "Spotify has charged you Rs.69 for 12 consecutive months — want to track it as a subscription?");
            try {
                alertsService.recordRecurringPaymentIfNotAlreadyTriggeredThisMonth(
                        userId, "spotify@okaxis", new BigDecimal("69"), payload);
                log.info("Created recurring-payment alert for Spotify");
            } catch (Exception e) {
                log.warn("Failed to create recurring-payment alert: {}", e.getMessage());
            }
        }
    }

    private void seedThresholdAlert(
            UUID userId,
            AlertType type,
            Integer categoryId,
            AlertPriority priority,
            Map<String, Object> payload,
            boolean markAsRead) {
        try {
            alertsService
                    .recordIfNotAlreadyTriggeredThisMonth(userId, type, categoryId, priority, payload)
                    .ifPresent(alert -> {
                        if (markAsRead) {
                            alertsService.markRead(userId, alert.id());
                        }
                    });
            log.info("Created alert: {} ({})", type, priority);
        } catch (Exception e) {
            log.warn("Failed to create alert {}: {}", type, e.getMessage());
        }
    }

    private void seedRecommendations(UUID userId) {
        log.info("Setting up demo user recommendations");

        seedRecommendation(
                userId,
                CATEGORY_SHOPPING,
                "You spent Rs.2,764 on Shopping this month — 32% more than last month. Consider setting a stricter weekly cap.",
                RecommendationPriority.MEDIUM);
        seedRecommendation(
                userId,
                CATEGORY_TRAVEL,
                "You spent Rs.2,229 on Travel this month via Rapido — clubbing trips could save on repeated base fares.",
                RecommendationPriority.LOW);
        seedRecommendation(
                userId,
                CATEGORY_FOOD,
                "You spent Rs.2,762 on Food / Dine Out this month — cooking 2 more meals at home each week could save around Rs.1,200/month.",
                RecommendationPriority.MEDIUM);
        seedRecommendation(
                userId,
                null,
                "Your salary of Rs.75,000 has been credited consistently every month — consider auto-transferring 20% (Rs.15,000) to savings right after payday.",
                RecommendationPriority.HIGH);
    }

    private void seedRecommendation(UUID userId, Integer categoryId, String text, RecommendationPriority priority) {
        try {
            recommendationsService.recordIfNoActiveRecommendationExists(userId, categoryId, text, priority);
            log.info("Created recommendation for category {}", categoryId);
        } catch (Exception e) {
            log.warn("Failed to create recommendation: {}", e.getMessage());
        }
    }
}
