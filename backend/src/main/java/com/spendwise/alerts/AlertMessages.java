package com.spendwise.alerts;

/** Human-readable title/body text per alert type — docs/user_flows.md "Handling an Alert" example text. */
final class AlertMessages {

    private AlertMessages() {}

    static String titleFor(AlertType type) {
        return switch (type) {
            case MID_MONTH_BUDGET -> "Mid-month budget alert";
            case CATEGORY_OVERSPEND -> "Budget exceeded";
            case CATEGORY_APPROACHING_LIMIT -> "Approaching your budget limit";
            case RECURRING_PAYMENT -> "New recurring payment detected";
        };
    }

    static String bodyFor(AlertType type) {
        return switch (type) {
            case MID_MONTH_BUDGET -> "You've spent 50% of your monthly budget by mid-month.";
            case CATEGORY_OVERSPEND -> "You've exceeded your budget for this category.";
            case CATEGORY_APPROACHING_LIMIT -> "You've spent 80% of your budget for this category.";
            case RECURRING_PAYMENT -> "A new recurring payment was detected.";
        };
    }
}
