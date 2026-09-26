package com.tripnest.backend.service;

import com.tripnest.backend.dto.TripRequest;
import com.tripnest.backend.dto.TripResponse;
import com.tripnest.backend.entity.Notification;
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
    private final NotificationService notificationService;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

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

        Trip saved = tripRepository.save(trip);

        // ✅ Auto notification
        notificationService.createNotification(
                user,
                "New trip created: " + saved.getTitle()
                        + " → " + saved.getDestination(),
                Notification.NotificationType.TRIP_REMINDER
        );

        return TripResponse.fromEntity(saved);
    }

    public List<TripResponse> getMyTrips() {
        User user = getCurrentUser();
        return tripRepository.findByUserId(user.getId())
                .stream()
                .map(TripResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public TripResponse getTripById(Long id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trip not found"));
        User user = getCurrentUser();
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        return TripResponse.fromEntity(trip);
    }

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

    public void deleteTrip(Long id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trip not found"));
        User user = getCurrentUser();
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        tripRepository.delete(trip);
    }

    public TripResponse updateTripStatus(Long id,
                                          Trip.TripStatus status) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trip not found"));
        User user = getCurrentUser();
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        trip.setStatus(status);

        // ✅ Status change notification
        notificationService.createNotification(
                user,
                "Trip status updated: " + trip.getTitle()
                        + " is now " + status.name(),
                Notification.NotificationType.TRAVEL_UPDATE
        );

        return TripResponse.fromEntity(tripRepository.save(trip));
    }
}