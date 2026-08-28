package com.tripnest.backend.dto;

import com.tripnest.backend.entity.Role;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    private Long id;
    private String name;
    private String email;
    private Role.RoleName role;
    private Boolean isActive;
    private LocalDateTime createdAt;
}