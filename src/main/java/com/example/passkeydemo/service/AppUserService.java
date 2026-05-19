package com.example.passkeydemo.service;

import com.example.passkeydemo.model.AppUser;
import com.example.passkeydemo.repo.UserRepository;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserService {

    private final UserRepository userRepository;

    public AppUser findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
    }
}
