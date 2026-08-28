package com.tripnest.backend.controllers;

import com.tripnest.backend.dto.AccountSettingsUpdateRequest;
import com.tripnest.backend.dto.UserProfileResponse;
import com.tripnest.backend.dto.UserProfileUpdateRequest;
import com.tripnest.backend.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userProfileService.getProfile(userDetails.getUsername()));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(userProfileService.updateProfile(userDetails.getUsername(), request));
    }

    @PutMapping("/settings")
    public ResponseEntity<UserProfileResponse> updateSettings(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody AccountSettingsUpdateRequest request) {
        return ResponseEntity.ok(userProfileService.updateSettings(userDetails.getUsername(), request));
    }

    @PostMapping("/favorites/{destinationId}")
    public ResponseEntity<UserProfileResponse> addFavoriteDestination(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long destinationId) {
        return ResponseEntity.ok(userProfileService.addFavoriteDestination(
                userDetails.getUsername(), destinationId));
    }

    @DeleteMapping("/favorites/{destinationId}")
    public ResponseEntity<UserProfileResponse> removeFavoriteDestination(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long destinationId) {
        return ResponseEntity.ok(userProfileService.removeFavoriteDestination(
                userDetails.getUsername(), destinationId));
    }
}