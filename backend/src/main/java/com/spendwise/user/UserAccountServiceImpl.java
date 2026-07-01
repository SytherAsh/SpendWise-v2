package com.spendwise.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserAccountServiceImpl implements UserAccountService {

    private final UserRepository userRepository;

    public UserAccountServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public Optional<User> findByPhone(String phone) {
        return userRepository.findByPhone(phone);
    }

    @Override
    @Transactional
    public User findOrCreateByPhone(String phone) {
        return userRepository.findByPhone(phone).orElseGet(() -> userRepository.createWithPhone(phone));
    }

    @Override
    @Transactional
    public Optional<User> findByGoogleId(String googleId) {
        return userRepository.findByGoogleId(googleId);
    }

    @Override
    @Transactional
    public User findOrCreateByGoogleId(String googleId, String email) {
        return userRepository.findByGoogleId(googleId)
                .orElseGet(() -> userRepository.createWithGoogleId(googleId, email));
    }

    @Override
    @Transactional
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }
}
