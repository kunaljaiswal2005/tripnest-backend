package com.tripnest.backend;

import com.tripnest.backend.entity.Role;
import com.tripnest.backend.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@RequiredArgsConstructor
public class BackendApplication implements CommandLineRunner {

    private final RoleRepository roleRepository;

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    @Override
    public void run(String... args) {
        for (Role.RoleName name : Role.RoleName.values()) {
            if (roleRepository.findByRoleName(name).isEmpty()) {
                Role role = new Role();
                role.setRoleName(name);
                roleRepository.save(role);
            }
        }
        System.out.println("✅ Roles seeded successfully!");
    }
}