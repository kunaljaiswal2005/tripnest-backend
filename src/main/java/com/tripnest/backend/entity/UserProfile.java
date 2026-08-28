package com.tripnest.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "user_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @ElementCollection
    @CollectionTable(name = "user_travel_preferences", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "preference")
    @Builder.Default
    private List<String> travelPreferences = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "user_favorite_destinations",
            joinColumns = @JoinColumn(name = "profile_id"),
            inverseJoinColumns = @JoinColumn(name = "destination_id"))
    @Builder.Default
    private Set<Destination> favoriteDestinations = new HashSet<>();
}