package com.tripnest.backend.service;

import com.tripnest.backend.dto.AccountSettingsUpdateRequest;
import com.tripnest.backend.dto.UserProfileResponse;
import com.tripnest.backend.dto.UserProfileUpdateRequest;
import com.tripnest.backend.entity.Destination;
import com.tripnest.backend.entity.User;
import com.tripnest.backend.entity.UserProfile;
import com.tripnest.backend.repository.DestinationRepository;
import com.tripnest.backend.repository.UserProfileRepository;
import com.tripnest.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final DestinationRepository destinationRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserProfileResponse getProfile(String email) {
        return toResponse(getOrCreateProfile(email));
    }

    @Transactional
    public UserProfileResponse updateProfile(String email, UserProfileUpdateRequest request) {
        UserProfile profile = getOrCreateProfile(email);
        profile.setBio(request.getBio());
        profile.setTravelPreferences(request.getTravelPreferences() == null
                ? new ArrayList<>() : new ArrayList<>(request.getTravelPreferences()));
        return toResponse(userProfileRepository.save(profile));
    }

    @Transactional
    public UserProfileResponse updateSettings(String email, AccountSettingsUpdateRequest request) {
        User user = getUser(email);

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered!");
        }

        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getCurrentPassword() == null
                    || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new RuntimeException("Current password is incorrect!");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        userRepository.save(user);
        return toResponse(getOrCreateProfile(user.getEmail()));
    }

    @Transactional
    public UserProfileResponse addFavoriteDestination(String email, Long destinationId) {
        UserProfile profile = getOrCreateProfile(email);
        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new RuntimeException("Destination not found!"));
        profile.getFavoriteDestinations().add(destination);
        return toResponse(userProfileRepository.save(profile));
    }

    @Transactional
    public UserProfileResponse removeFavoriteDestination(String email, Long destinationId) {
        UserProfile profile = getOrCreateProfile(email);
        profile.getFavoriteDestinations().removeIf(destination -> destination.getId().equals(destinationId));
        return toResponse(userProfileRepository.save(profile));
    }

    private UserProfile getOrCreateProfile(String email) {
        User user = getUser(email);
        return userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> userProfileRepository.save(UserProfile.builder()
                        .user(user)
                        .build()));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found!"));
    }

    private UserProfileResponse toResponse(UserProfile profile) {
        User user = profile.getUser();
        List<UserProfileResponse.DestinationSummary> destinations = profile.getFavoriteDestinations()
                .stream()
                .map(destination -> UserProfileResponse.DestinationSummary.builder()
                        .id(destination.getId())
                        .name(destination.getName())
                        .country(destination.getCountry())
                        .build())
                .toList();

        return UserProfileResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .bio(profile.getBio())
                .travelPreferences(profile.getTravelPreferences())
                .favoriteDestinations(destinations)
                .build();
    }
}