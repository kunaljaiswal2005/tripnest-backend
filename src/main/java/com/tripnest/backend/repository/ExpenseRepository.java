package com.tripnest.backend.repository;

import com.tripnest.backend.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseRepository
        extends JpaRepository<Expense, Long> {
    List<Expense> findByTripId(Long tripId);
    List<Expense> findByTripIdAndCategory(
            Long tripId, Expense.ExpenseCategory category);
}