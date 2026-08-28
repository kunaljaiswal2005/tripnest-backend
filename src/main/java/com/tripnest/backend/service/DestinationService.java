package com.tripnest.backend.service;

import com.tripnest.backend.dto.DestinationRequest;
import com.tripnest.backend.dto.DestinationResponse;
import com.tripnest.backend.entity.Destination;
import com.tripnest.backend.repository.DestinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DestinationService {

    private final DestinationRepository destinationRepository;

    public List<DestinationResponse> getAllDestinations() {
        return destinationRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public DestinationResponse getDestinationById(Long id) {
        return toResponse(findDestination(id));
    }

    public DestinationResponse createDestination(DestinationRequest request) {
        Destination destination = new Destination();
        updateEntity(destination, request);
        return toResponse(destinationRepository.save(destination));
    }

    public DestinationResponse updateDestination(Long id, DestinationRequest request) {
        Destination destination = findDestination(id);
        updateEntity(destination, request);
        return toResponse(destinationRepository.save(destination));
    }

    public void deleteDestination(Long id) {
        Destination destination = findDestination(id);
        destinationRepository.delete(destination);
    }

    private Destination findDestination(Long id) {
        return destinationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Destination not found!"));
    }

    private void updateEntity(Destination destination, DestinationRequest request) {
        destination.setName(request.getName());
        destination.setCountry(request.getCountry());
        destination.setDescription(request.getDescription());
        destination.setImageUrl(request.getImageUrl());
        destination.setBestTimeToVisit(request.getBestTimeToVisit());
        destination.setIsPopular(request.getIsPopular());
    }

    private DestinationResponse toResponse(Destination destination) {
        return DestinationResponse.builder()
                .id(destination.getId())
                .name(destination.getName())
                .country(destination.getCountry())
                .description(destination.getDescription())
                .imageUrl(destination.getImageUrl())
                .bestTimeToVisit(destination.getBestTimeToVisit())
                .isPopular(destination.getIsPopular())
                .build();
    }
}
