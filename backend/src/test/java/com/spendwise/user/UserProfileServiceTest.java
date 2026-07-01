package com.spendwise.user;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserProfileServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserProfileService service = new UserProfileService(userRepository);

    @Test
    void updateEmailPersistsThenReturnsUpdatedUser() {
        UUID userId = UUID.randomUUID();
        User before = new User(userId, "+911234567890", null, null, Instant.now());
        User after = new User(userId, "+911234567890", "new@example.com", null, Instant.now());
        given(userRepository.findById(userId)).willReturn(Optional.of(before), Optional.of(after));

        User result = service.updateEmail(userId, "new@example.com");

        verify(userRepository).updateEmail(userId, "new@example.com");
        assertThat(result.email()).isEqualTo("new@example.com");
    }

    @Test
    void getProfileThrowsWhenUserMissing() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(userId)).isInstanceOf(UserNotFoundException.class);
    }
}
