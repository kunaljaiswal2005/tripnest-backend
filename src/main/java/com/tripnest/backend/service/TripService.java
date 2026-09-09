package com.tripnest.backend.service;

import com.tripnest.backend.dto.TripRequest;
import com.tripnest.backend.dto.TripResponse;
import com.tripnest.backend.entity.Trip;
import com.tripnest.backend.entity.User;
import com.tripnest.backend.repository.TripRepository;
import com.tripnest.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    // Current logged in user nikalo
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // Trip banao
    public TripResponse createTrip(TripRequest request) {
        User user = getCurrentUser();

        Trip trip = Trip.builder()
                .title(request.getTitle())
                .destination(request.getDestination())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalBudget(request.getTotalBudget())
                .description(request.getDescription())
                .coverImage(request.getCoverImage())
                .status(request.getStatus() != null ?
                        request.getStatus() : Trip.TripStatus.PLANNING)
                .user(user)
                .build();

        return TripResponse.fromEntity(tripRepository.save(trip));
    }

    // Apni saari trips dekho
    public List<TripResponse> getMyTrips() {
        User user = getCurrentUser();
        return tripRepository.findByUserId(user.getId())
                .stream()
                .map(TripResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // Ek trip ki detail
    public TripResponse getTripById(Long id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        // Sirf apni trip dekh sako
        User user = getCurrentUser();
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        return TripResponse.fromEntity(trip);
    }

    // Trip update karo
    public TripResponse updateTrip(Long id, TripRequest request) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        User user = getCurrentUser();
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        trip.setTitle(request.getTitle());
        trip.setDestination(request.getDestination());
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setTotalBudget(request.getTotalBudget());
        trip.setDescription(request.getDescription());
        trip.setCoverImage(request.getCoverImage());
        if (request.getStatus() != null) {
            trip.setStatus(request.getStatus());
        }

        return TripResponse.fromEntity(tripRepository.save(trip));
    }

    // Trip delete karo
    public void deleteTrip(Long id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        User user = getCurrentUser();
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        tripRepository.delete(trip);
    }

    // Status update karo
    public TripResponse updateTripStatus(Long id, Trip.TripStatus status) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        User user = getCurrentUser();
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        trip.setStatus(status);
        return TripResponse.fromEntity(tripRepository.save(trip));
    }
}