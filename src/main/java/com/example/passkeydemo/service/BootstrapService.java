package com.example.passkeydemo.service;

import com.example.passkeydemo.model.AppUser;
import com.example.passkeydemo.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BootstrapService implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        userRepository.findByUsername("demo").orElseGet(() -> {
            AppUser user = new AppUser();
            user.setUsername("demo");
            user.setDisplayName("Demo User");
            user.setPasswordHash(passwordEncoder.encode("pass1234"));
            return userRepository.save(user);
        });
    }
}
