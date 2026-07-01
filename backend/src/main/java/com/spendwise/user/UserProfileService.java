package com.spendwise.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserProfileService {

    private final UserRepository userRepository;

    public UserProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User getProfile(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    @Transactional
    public User updateEmail(UUID userId, String email) {
        userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        userRepository.updateEmail(userId, email);
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
