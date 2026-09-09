package com.tripnest.backend.service;

import com.tripnest.backend.dto.ItineraryRequest;
import com.tripnest.backend.dto.ItineraryResponse;
import com.tripnest.backend.entity.Itinerary;
import com.tripnest.backend.entity.Trip;
import com.tripnest.backend.entity.User;
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
public class ItineraryService {

    private final ItineraryRepository itineraryRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Trip verifyTripOwner(Long tripId) {
        User user = getCurrentUser();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        return trip;
    }

    // Day/Itinerary banao
    public ItineraryResponse createItinerary(Long tripId,
                                              ItineraryRequest request) {
        Trip trip = verifyTripOwner(tripId);

        // Check karo same day already exist toh nahi
        boolean exists = itineraryRepository
                .findByTripId(tripId)
                .stream()
                .anyMatch(i -> i.getDayNumber()
                        .equals(request.getDayNumber()));

        if (exists) {
            throw new RuntimeException(
                "Day " + request.getDayNumber() + " already exists!");
        }

        Itinerary itinerary = Itinerary.builder()
                .dayNumber(request.getDayNumber())
                .date(request.getDate())
                .notes(request.getNotes())
                .trip(trip)
                .build();

        return ItineraryResponse.fromEntity(
                itineraryRepository.save(itinerary));
    }

    // Trip ke saare days dekho
    public List<ItineraryResponse> getItinerariesByTrip(Long tripId) {
        verifyTripOwner(tripId);
        return itineraryRepository.findByTripId(tripId)
                .stream()
                .sorted((a, b) -> a.getDayNumber()
                        .compareTo(b.getDayNumber()))
                .map(ItineraryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // Ek itinerary detail
    public ItineraryResponse getItineraryById(Long id) {
        Itinerary itinerary = itineraryRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Itinerary not found"));
        verifyTripOwner(itinerary.getTrip().getId());
        return ItineraryResponse.fromEntity(itinerary);
    }

    // Itinerary update karo
    public ItineraryResponse updateItinerary(Long id,
                                              ItineraryRequest request) {
        Itinerary itinerary = itineraryRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Itinerary not found"));
        verifyTripOwner(itinerary.getTrip().getId());

        itinerary.setDayNumber(request.getDayNumber());
        itinerary.setDate(request.getDate());
        itinerary.setNotes(request.getNotes());

        return ItineraryResponse.fromEntity(
                itineraryRepository.save(itinerary));
    }

    // Itinerary delete karo
    public void deleteItinerary(Long id) {
        Itinerary itinerary = itineraryRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Itinerary not found"));
        verifyTripOwner(itinerary.getTrip().getId());
        itineraryRepository.delete(itinerary);
    }

    // Trip ke liye automatically saare days generate karo
    public List<ItineraryResponse> generateDays(Long tripId) {
        Trip trip = verifyTripOwner(tripId);

        if (trip.getStartDate() == null || trip.getEndDate() == null) {
            throw new RuntimeException(
                    "Trip start/end date set nahi hai!");
        }

        // Pehle se bane days delete karo
        itineraryRepository.deleteAll(
                itineraryRepository.findByTripId(tripId));

        // Har din ke liye itinerary banao
        List<Itinerary> days = new java.util.ArrayList<>();
        java.time.LocalDate current = trip.getStartDate();
        int dayNum = 1;

        while (!current.isAfter(trip.getEndDate())) {
            Itinerary day = Itinerary.builder()
                    .dayNumber(dayNum)
                    .date(current)
                    .notes("Day " + dayNum + " - " + trip.getDestination())
                    .trip(trip)
                    .build();
            days.add(day);
            current = current.plusDays(1);
            dayNum++;
        }

        return itineraryRepository.saveAll(days)
                .stream()
                .map(ItineraryResponse::fromEntity)
                .collect(Collectors.toList());
    }
}