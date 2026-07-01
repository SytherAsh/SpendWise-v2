package com.spendwise.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Required tests for E1-S1-T2 (hashing) and E1-S1-T5 (rotation + replay detection). */
class RefreshTokenServiceTest {

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final RefreshTokenService service = new RefreshTokenService(repository);

    @Test
    void issuedTokenIsPersistedAsShaHashNotEqualToRawToken() {
        UUID userId = UUID.randomUUID();
        given(repository.insert(any(), anyString(), any()))
                .willAnswer(invocation -> new RefreshToken(
                        UUID.randomUUID(), userId, invocation.getArgument(1), Instant.now(), invocation.getArgument(2), null));

        RefreshTokenService.IssuedRefreshToken issued = service.issue(userId);

        String expectedHash = RefreshTokenService.hash(issued.rawToken());
        verify(repository).insert(userId, expectedHash, issued.expiresAt());
        assertThat(expectedHash).isNotEqualTo(issued.rawToken());
    }

    @Test
    void normalRotationRevokesOldTokenAndIssuesNewOne() {
        UUID userId = UUID.randomUUID();
        String oldRawToken = "old-raw-token";
        String oldHash = RefreshTokenService.hash(oldRawToken);
        UUID oldId = UUID.randomUUID();
        RefreshToken existing = new RefreshToken(oldId, userId, oldHash, Instant.now(), Instant.now().plus(10, ChronoUnit.DAYS), null);
        given(repository.findByTokenHash(oldHash)).willReturn(Optional.of(existing));

        RefreshTokenService.RotationResult result = service.rotate(oldRawToken);

        verify(repository).revoke(oldId, userId);
        verify(repository).insert(any(), anyString(), any());
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.newRawToken()).isNotEqualTo(oldRawToken);
    }

    @Test
    void replayingAnAlreadyRotatedTokenRevokesAllSessionsForThatUser() {
        UUID userId = UUID.randomUUID();
        String rawToken = "already-rotated-token";
        String hash = RefreshTokenService.hash(rawToken);
        RefreshToken alreadyRevoked =
                new RefreshToken(UUID.randomUUID(), userId, hash, Instant.now(), Instant.now().plus(10, ChronoUnit.DAYS), Instant.now());
        given(repository.findByTokenHash(hash)).willReturn(Optional.of(alreadyRevoked));

        assertThatThrownBy(() -> service.rotate(rawToken)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository).revokeAllForUser(userId);
        verify(repository, never()).insert(any(), anyString(), any());
    }

    @Test
    void unknownTokenIsRejected() {
        given(repository.findByTokenHash(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("never-issued")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        UUID userId = UUID.randomUUID();
        String rawToken = "expired-token";
        String hash = RefreshTokenService.hash(rawToken);
        RefreshToken expired =
                new RefreshToken(UUID.randomUUID(), userId, hash, Instant.now().minus(40, ChronoUnit.DAYS), Instant.now().minus(10, ChronoUnit.DAYS), null);
        given(repository.findByTokenHash(hash)).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate(rawToken)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, times(0)).revoke(any(), any());
    }

    @Test
    void logoutRevokesThePresentedToken() {
        UUID userId = UUID.randomUUID();
        String rawToken = "session-token";
        String hash = RefreshTokenService.hash(rawToken);
        UUID id = UUID.randomUUID();
        RefreshToken existing = new RefreshToken(id, userId, hash, Instant.now(), Instant.now().plus(10, ChronoUnit.DAYS), null);
        given(repository.findByTokenHash(hash)).willReturn(Optional.of(existing));

        service.logout(rawToken, userId);

        verify(repository).revoke(id, userId);
    }

    @Test
    void logoutRejectsATokenBelongingToAnotherUser() {
        UUID ownerUserId = UUID.randomUUID();
        UUID callerUserId = UUID.randomUUID();
        String rawToken = "someone-elses-token";
        String hash = RefreshTokenService.hash(rawToken);
        RefreshToken existing =
                new RefreshToken(UUID.randomUUID(), ownerUserId, hash, Instant.now(), Instant.now().plus(10, ChronoUnit.DAYS), null);
        given(repository.findByTokenHash(hash)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.logout(rawToken, callerUserId))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).revoke(any(), any());
    }
}
