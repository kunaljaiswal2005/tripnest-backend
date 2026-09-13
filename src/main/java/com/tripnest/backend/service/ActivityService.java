package com.tripnest.backend.service;

import com.tripnest.backend.dto.ActivityRequest;
import com.tripnest.backend.dto.ActivityResponse;
import com.tripnest.backend.entity.Activity;
import com.tripnest.backend.entity.Itinerary;
import com.tripnest.backend.entity.User;
import com.tripnest.backend.repository.ActivityRepository;
import com.tripnest.backend.repository.ItineraryRepository;
import com.tripnest.backend.repository.TripRepository;
import com.tripnest.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ItineraryRepository itineraryRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Itinerary verifyAccess(Long itineraryId) {
        User user = getCurrentUser();
        Itinerary itinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() ->
                        new RuntimeException("Itinerary not found"));

        if (!itinerary.getTrip().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        return itinerary;
    }

    // Activity add karo
    public ActivityResponse createActivity(Long itineraryId,
                                           ActivityRequest request) {
        Itinerary itinerary = verifyAccess(itineraryId);

        Activity activity = Activity.builder()
                .title(request.getTitle())
                .activityType(request.getActivityType())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .location(request.getLocation())
                .notes(request.getNotes())
                .estimatedCost(request.getEstimatedCost())
                .itinerary(itinerary)
                .build();

        return ActivityResponse.fromEntity(
                activityRepository.save(activity));
    }

    // Ek din ki saari activities
    public List<ActivityResponse> getActivitiesByItinerary(
            Long itineraryId) {
        verifyAccess(itineraryId);
        return activityRepository.findByItineraryId(itineraryId)
                .stream()
                .sorted((a, b) -> {
                    if (a.getStartTime() == null) return 1;
                    if (b.getStartTime() == null) return -1;
                    return a.getStartTime().compareTo(b.getStartTime());
                })
                .map(ActivityResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // Ek activity detail
    public ActivityResponse getActivityById(Long id) {
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Activity not found"));
        verifyAccess(activity.getItinerary().getId());
        return ActivityResponse.fromEntity(activity);
    }

    // Activity update karo
    public ActivityResponse updateActivity(Long id,
                                           ActivityRequest request) {
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Activity not found"));
        verifyAccess(activity.getItinerary().getId());

        activity.setTitle(request.getTitle());
        activity.setActivityType(request.getActivityType());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setLocation(request.getLocation());
        activity.setNotes(request.getNotes());
        activity.setEstimatedCost(request.getEstimatedCost());

        return ActivityResponse.fromEntity(
                activityRepository.save(activity));
    }

    // Activity delete karo
    public void deleteActivity(Long id) {
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Activity not found"));
        verifyAccess(activity.getItinerary().getId());
        activityRepository.delete(activity);
    }

    // Trip ki saari activities ka total cost
    public Double getTotalCostByItinerary(Long itineraryId) {
        verifyAccess(itineraryId);
        return activityRepository.findByItineraryId(itineraryId)
                .stream()
                .filter(a -> a.getEstimatedCost() != null)
                .mapToDouble(Activity::getEstimatedCost)
                .sum();
    }
}