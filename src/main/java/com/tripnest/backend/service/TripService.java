package com.tripnest.backend.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.tripnest.backend.dto.TripRequest;
import com.tripnest.backend.dto.TripResponse;
import com.tripnest.backend.entity.Notification;
import com.tripnest.backend.entity.Trip;
import com.tripnest.backend.entity.User;
import com.tripnest.backend.repository.TripRepository;
import com.tripnest.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // ============================================================
    // HELPERS
    // ============================================================

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
    }

    // ============================================================
    // CREATE
    // ============================================================

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
                .status(request.getStatus() != null
                        ? request.getStatus()
                        : Trip.TripStatus.PLANNING)
                .user(user)
                .build();

        Trip saved = tripRepository.save(trip);

        // ✅ Notification with referenceId
        notificationService.createNotification(
                user,
                "New trip created: \""
                        + saved.getTitle()
                        + "\" → " + saved.getDestination(),
                Notification.NotificationType.TRIP_REMINDER,
                saved.getId(),
                Notification.ReferenceType.TRIP
        );

        return TripResponse.fromEntity(saved);
    }

    // ============================================================
    // GET ALL
    // ============================================================

    public List<TripResponse> getMyTrips() {
        User user = getCurrentUser();
        return tripRepository.findByUserId(user.getId())
                .stream()
                .map(TripResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // ============================================================
    // GET BY ID
    // ============================================================

    public TripResponse getTripById(Long id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Trip not found"));
        User user = getCurrentUser();
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        return TripResponse.fromEntity(trip);
    }

    // ============================================================
    // UPDATE
    // ============================================================

    public TripResponse updateTrip(Long id,
                                    TripRequest request) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Trip not found"));
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

        Trip saved = tripRepository.save(trip);

        // ✅ Notification
        notificationService.createNotification(
                user,
                "Trip updated: \"" + saved.getTitle() + "\"",
                Notification.NotificationType.TRAVEL_UPDATE,
                saved.getId(),
                Notification.ReferenceType.TRIP
        );

        return TripResponse.fromEntity(saved);
    }

    // ============================================================
    // DELETE
    // ============================================================

    public void deleteTrip(Long id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Trip not found"));
        User user = getCurrentUser();
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        tripRepository.delete(trip);
    }

    // ============================================================
    // STATUS UPDATE
    // ============================================================

    public TripResponse updateTripStatus(Long id,
                                          Trip.TripStatus status) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Trip not found"));
        User user = getCurrentUser();
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        trip.setStatus(status);
        Trip saved = tripRepository.save(trip);

        // ✅ Notification
        notificationService.createNotification(
                user,
                "Trip \"" + saved.getTitle()
                        + "\" status changed to "
                        + status.name(),
                Notification.NotificationType.TRAVEL_UPDATE,
                saved.getId(),
                Notification.ReferenceType.TRIP
        );

        return TripResponse.fromEntity(saved);
    }
}