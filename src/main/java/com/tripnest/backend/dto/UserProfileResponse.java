package com.tripnest.backend.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private Long userId;
    private String name;
    private String email;
    private String bio;
    private List<String> travelPreferences;
    private List<DestinationSummary> favoriteDestinations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DestinationSummary {
        private Long id;
        private String name;
        private String country;
    }
}