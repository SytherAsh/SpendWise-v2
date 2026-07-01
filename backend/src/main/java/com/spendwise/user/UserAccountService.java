package com.spendwise.user;

import java.util.Optional;
import java.util.UUID;

/**
 * Service interface consumed by other modules (Auth) that need to resolve or create a user
 * account — per CLAUDE.md, cross-module calls go through injected interfaces only.
 */
public interface UserAccountService {

    Optional<User> findByPhone(String phone);

    User findOrCreateByPhone(String phone);

    Optional<User> findByGoogleId(String googleId);

    User findOrCreateByGoogleId(String googleId, String email);

    Optional<User> findById(UUID id);
}
