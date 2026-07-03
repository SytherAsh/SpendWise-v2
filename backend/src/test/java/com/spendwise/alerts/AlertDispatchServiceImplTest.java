package com.spendwise.alerts;

import com.spendwise.user.User;
import com.spendwise.user.UserAccountService;
import com.spendwise.user.UserPreferences;
import com.spendwise.user.UserPreferencesService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Required tests for E5-S3-T1/T2 (docs/testing.md; FCM/SMTP dispatch success/failure paths). */
class AlertDispatchServiceImplTest {

    private final FcmClient fcmClient = mock(FcmClient.class);
    private final MailClient mailClient = mock(MailClient.class);
    private final UserAccountService userAccountService = mock(UserAccountService.class);
    private final UserPreferencesService userPreferencesService = mock(UserPreferencesService.class);
    private final AlertsService alertsService = mock(AlertsService.class);
    private final AlertDispatchServiceImpl dispatchService = new AlertDispatchServiceImpl(
            fcmClient, mailClient, userAccountService, userPreferencesService, alertsService);

    private final UUID userId = UUID.randomUUID();
    private final Alert alert =
            new Alert(UUID.randomUUID(), userId, AlertType.CATEGORY_OVERSPEND, AlertPriority.HIGH, Instant.now(), null, false, Map.of());

    @Test
    void successfulFcmSendSetsDeliveredAt() {
        given(userPreferencesService.getPreferences(userId))
                .willReturn(new UserPreferences(userId, Map.of("push", true, "email", false), java.util.List.of(), java.util.List.of(), null, "device-token"));

        dispatchService.dispatch(alert);

        verify(fcmClient).send(eq("device-token"), any(), any());
        verify(alertsService).markDelivered(userId, alert.id());
    }

    @Test
    void failedFcmSendLeavesDeliveredAtNullWithoutThrowing() {
        given(userPreferencesService.getPreferences(userId))
                .willReturn(new UserPreferences(userId, Map.of("push", true, "email", false), java.util.List.of(), java.util.List.of(), null, "device-token"));
        doThrow(new RuntimeException("FCM unavailable")).when(fcmClient).send(any(), any(), any());

        org.assertj.core.api.Assertions.assertThatCode(() -> dispatchService.dispatch(alert)).doesNotThrowAnyException();

        verify(alertsService, never()).markDelivered(any(), any());
    }

    @Test
    void noFcmTokenSkipsPushGracefully() {
        given(userPreferencesService.getPreferences(userId))
                .willReturn(new UserPreferences(userId, Map.of("push", true, "email", false), java.util.List.of(), java.util.List.of(), null, null));

        dispatchService.dispatch(alert);

        verify(fcmClient, never()).send(any(), any(), any());
        verify(alertsService, never()).markDelivered(any(), any());
    }

    @Test
    void pushChannelDisabledInPreferencesSkipsFcmEvenWithAToken() {
        given(userPreferencesService.getPreferences(userId))
                .willReturn(new UserPreferences(userId, Map.of("push", false, "email", false), java.util.List.of(), java.util.List.of(), null, "device-token"));

        dispatchService.dispatch(alert);

        verify(fcmClient, never()).send(any(), any(), any());
    }

    @Test
    void successfulSmtpSendSetsDeliveredAt() {
        given(userPreferencesService.getPreferences(userId))
                .willReturn(new UserPreferences(userId, Map.of("push", false, "email", true), java.util.List.of(), java.util.List.of(), null, null));
        given(userAccountService.findById(userId))
                .willReturn(Optional.of(new User(userId, "+919999999999", "user@example.com", null, Instant.now())));

        dispatchService.dispatch(alert);

        verify(mailClient).send(eq("user@example.com"), any(), any());
        verify(alertsService).markDelivered(userId, alert.id());
    }

    @Test
    void failedSmtpSendLeavesDeliveredAtNullWithoutThrowing() {
        given(userPreferencesService.getPreferences(userId))
                .willReturn(new UserPreferences(userId, Map.of("push", false, "email", true), java.util.List.of(), java.util.List.of(), null, null));
        given(userAccountService.findById(userId))
                .willReturn(Optional.of(new User(userId, "+919999999999", "user@example.com", null, Instant.now())));
        doThrow(new RuntimeException("SMTP unavailable")).when(mailClient).send(any(), any(), any());

        org.assertj.core.api.Assertions.assertThatCode(() -> dispatchService.dispatch(alert)).doesNotThrowAnyException();

        verify(alertsService, never()).markDelivered(any(), any());
    }

    @Test
    void noEmailOnAccountSkipsSmtpGracefully() {
        given(userPreferencesService.getPreferences(userId))
                .willReturn(new UserPreferences(userId, Map.of("push", false, "email", true), java.util.List.of(), java.util.List.of(), null, null));
        given(userAccountService.findById(userId)).willReturn(Optional.of(new User(userId, "+919999999999", null, null, Instant.now())));

        dispatchService.dispatch(alert);

        verify(mailClient, never()).send(any(), any(), any());
        verify(alertsService, never()).markDelivered(any(), any());
    }

    @Test
    void bothChannelsSucceedingOnlyMarksDeliveredOnce() {
        given(userPreferencesService.getPreferences(userId))
                .willReturn(new UserPreferences(userId, Map.of("push", true, "email", true), java.util.List.of(), java.util.List.of(), null, "device-token"));
        given(userAccountService.findById(userId))
                .willReturn(Optional.of(new User(userId, "+919999999999", "user@example.com", null, Instant.now())));

        dispatchService.dispatch(alert);

        verify(alertsService, times(1)).markDelivered(userId, alert.id());
    }

    @Test
    void bothChannelsFailingNeverMarksDelivered() {
        given(userPreferencesService.getPreferences(userId))
                .willReturn(new UserPreferences(userId, Map.of("push", true, "email", true), java.util.List.of(), java.util.List.of(), null, "device-token"));
        given(userAccountService.findById(userId))
                .willReturn(Optional.of(new User(userId, "+919999999999", "user@example.com", null, Instant.now())));
        doThrow(new RuntimeException("FCM unavailable")).when(fcmClient).send(any(), any(), any());
        doThrow(new RuntimeException("SMTP unavailable")).when(mailClient).send(any(), any(), any());

        dispatchService.dispatch(alert);

        verify(alertsService, never()).markDelivered(any(), any());
    }
}
