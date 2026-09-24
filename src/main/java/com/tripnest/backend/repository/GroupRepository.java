package com.tripnest.backend.repository;

import com.tripnest.backend.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupRepository
        extends JpaRepository<Group, Long> {
    List<Group> findByCreatedById(Long userId);
    List<Group> findByTripId(Long tripId);
}