package com.spendwise.alerts;

import com.spendwise.user.User;
import com.spendwise.user.UserAccountService;
import com.spendwise.user.UserPreferences;
import com.spendwise.user.UserPreferencesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * E5-S3 — dispatches a high-priority alert via FCM push and SMTP email, per docs/requirements.md's
 * priority/delivery table ("High — push notification + email"). Reads {@code email}/{@code
 * fcm_token}/{@code alert_channels} from the User module through its injected service interfaces
 * (docs/architecture.md's Alerts→User addendum, added this epic).
 */
@Service
public class AlertDispatchServiceImpl implements AlertDispatchService {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatchServiceImpl.class);

    private final FcmClient fcmClient;
    private final MailClient mailClient;
    private final UserAccountService userAccountService;
    private final UserPreferencesService userPreferencesService;
    private final AlertsService alertsService;

    public AlertDispatchServiceImpl(
            FcmClient fcmClient,
            MailClient mailClient,
            UserAccountService userAccountService,
            UserPreferencesService userPreferencesService,
            AlertsService alertsService) {
        this.fcmClient = fcmClient;
        this.mailClient = mailClient;
        this.userAccountService = userAccountService;
        this.userPreferencesService = userPreferencesService;
        this.alertsService = alertsService;
    }

    @Override
    public void dispatch(Alert alert) {
        UserPreferences preferences = userPreferencesService.getPreferences(alert.userId());
        boolean delivered = false;

        if (channelEnabled(preferences, "push") && StringUtils.hasText(preferences.fcmToken())) {
            delivered |= sendPush(alert, preferences.fcmToken());
        }

        if (channelEnabled(preferences, "email")) {
            delivered |= userAccountService
                    .findById(alert.userId())
                    .map(User::email)
                    .filter(StringUtils::hasText)
                    .map(email -> sendEmail(alert, email))
                    .orElse(false);
        }

        // Set delivered_at once at most, regardless of how many channels succeeded — avoids the
        // "duplicate delivered_at update" ambiguity the E5-S3-T2 DoD calls out when both channels
        // succeed/fail independently.
        if (delivered) {
            alertsService.markDelivered(alert.userId(), alert.id());
        }
    }

    private boolean sendPush(Alert alert, String fcmToken) {
        try {
            fcmClient.send(fcmToken, AlertMessages.titleFor(alert.type()), AlertMessages.bodyFor(alert.type()));
            return true;
        } catch (RuntimeException e) {
            // Never propagate — a failed push must not crash the evaluator job (E5-S3-T1 DoD).
            log.warn("FCM push dispatch failed for alert {}: {}", alert.id(), e.getMessage());
            return false;
        }
    }

    private boolean sendEmail(Alert alert, String email) {
        try {
            mailClient.send(email, AlertMessages.titleFor(alert.type()), AlertMessages.bodyFor(alert.type()));
            return true;
        } catch (RuntimeException e) {
            // Never propagate — a failed send must not crash the evaluator job (E5-S3-T2 DoD).
            log.warn("SMTP email dispatch failed for alert {}: {}", alert.id(), e.getMessage());
            return false;
        }
    }

    private static boolean channelEnabled(UserPreferences preferences, String channel) {
        return preferences.alertChannels().getOrDefault(channel, true);
    }
}
