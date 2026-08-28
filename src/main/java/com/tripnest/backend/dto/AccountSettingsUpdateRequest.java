package com.tripnest.backend.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSettingsUpdateRequest {
    private String name;
    private String email;
    private String currentPassword;
    private String newPassword;
}