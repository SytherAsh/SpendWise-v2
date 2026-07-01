package com.spendwise.user;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Required tests for E1-S4-T1 (docs/testing.md Ingest dual-auth validation, device-key half):
 * valid active key for the correct user -> success + last_used_at updated; missing -> reject;
 * inactive key -> reject; key belonging to a different user_id -> reject.
 */
class DeviceApiKeyServiceImplTest {

    private final DeviceApiKeyRepository repository = mock(DeviceApiKeyRepository.class);
    private final DeviceApiKeyServiceImpl service = new DeviceApiKeyServiceImpl(repository);

    @Test
    void validActiveKeyForCorrectUserSucceedsAndUpdatesLastUsed() {
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        String[] capturedHash = new String[1];
        given(repository.insert(eq(userId), anyString())).willAnswer(invocation -> {
            capturedHash[0] = invocation.getArgument(1);
            return new DeviceApiKey(keyId, userId, capturedHash[0], Instant.now(), null, true);
        });
        String rawKey = service.registerNewKey(userId);
        given(repository.findActiveForUser(userId))
                .willReturn(List.of(new DeviceApiKey(keyId, userId, capturedHash[0], Instant.now(), null, true)));

        boolean result = service.validate(rawKey, userId);

        assertThat(result).isTrue();
        verify(repository).markLastUsed(keyId, userId);
    }

    @Test
    void missingKeyIsRejected() {
        UUID userId = UUID.randomUUID();
        given(repository.findActiveForUser(userId)).willReturn(List.of());

        assertThat(service.validate(null, userId)).isFalse();
        assertThat(service.validate("some-key", userId)).isFalse();
        verify(repository, never()).markLastUsed(any(), any());
    }

    @Test
    void inactiveKeyIsRejected() {
        UUID userId = UUID.randomUUID();
        // findActiveForUser only ever returns is_active = TRUE rows (query-level filter), so an
        // inactive key never appears here — validation against it necessarily fails.
        given(repository.findActiveForUser(userId)).willReturn(List.of());

        assertThat(service.validate("a-previously-issued-but-now-inactive-key", userId)).isFalse();
    }

    @Test
    void keyBelongingToADifferentUserIsRejected() {
        UUID ownerUserId = UUID.randomUUID();
        UUID callerUserId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        String[] capturedHash = new String[1];
        given(repository.insert(eq(ownerUserId), anyString())).willAnswer(invocation -> {
            capturedHash[0] = invocation.getArgument(1);
            return new DeviceApiKey(keyId, ownerUserId, capturedHash[0], Instant.now(), null, true);
        });
        String rawKey = service.registerNewKey(ownerUserId);
        // The caller queries scoped to their own user_id — the owner's key never appears.
        given(repository.findActiveForUser(callerUserId)).willReturn(List.of());

        assertThat(service.validate(rawKey, callerUserId)).isFalse();
    }
}
