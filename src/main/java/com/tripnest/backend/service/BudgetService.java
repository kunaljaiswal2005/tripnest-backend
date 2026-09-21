package com.tripnest.backend.service;

import com.tripnest.backend.dto.BudgetRequest;
import com.tripnest.backend.dto.BudgetResponse;
import com.tripnest.backend.entity.Budget;
import com.tripnest.backend.entity.Expense;
import com.tripnest.backend.entity.Trip;
import com.tripnest.backend.entity.User;
import com.tripnest.backend.repository.BudgetRepository;
import com.tripnest.backend.repository.ExpenseRepository;
import com.tripnest.backend.repository.TripRepository;
import com.tripnest.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;

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

    // Budget set karo
    public BudgetResponse createOrUpdateBudget(Long tripId,
                                                BudgetRequest request) {
        Trip trip = verifyTripOwner(tripId);

        // Agar pehle se budget hai toh update karo
        Budget budget = budgetRepository.findByTripId(tripId)
                .orElse(new Budget());

        budget.setTotalAmount(request.getTotalAmount());
        budget.setCurrency(request.getCurrency() != null
                ? request.getCurrency() : "INR");
        budget.setTransportationBudget(request.getTransportationBudget());
        budget.setHotelBudget(request.getHotelBudget());
        budget.setFoodBudget(request.getFoodBudget());
        budget.setShoppingBudget(request.getShoppingBudget());
        budget.setEntertainmentBudget(request.getEntertainmentBudget());
        budget.setMiscBudget(request.getMiscBudget());
        budget.setTrip(trip);

        Budget saved = budgetRepository.save(budget);
        return BudgetResponse.fromEntity(saved);
    }

    // Budget dekho
    public BudgetResponse getBudgetByTrip(Long tripId) {
        verifyTripOwner(tripId);

        Budget budget = budgetRepository.findByTripId(tripId)
                .orElseThrow(() ->
                        new RuntimeException("Budget not set for this trip"));

        BudgetResponse res = BudgetResponse.fromEntity(budget);

        // Total spent calculate karo
        List<Expense> expenses = expenseRepository.findByTripId(tripId);
        double totalSpent = expenses.stream()
                .filter(e -> e.getAmount() != null)
                .mapToDouble(Expense::getAmount)
                .sum();

        res.setTotalSpent(totalSpent);
        res.setRemainingBudget(budget.getTotalAmount() - totalSpent);

        // Percentage calculate karo
        if (budget.getTotalAmount() > 0) {
            double percentage = (totalSpent / budget.getTotalAmount()) * 100;
            res.setSpentPercentage(Math.round(percentage * 100.0) / 100.0);
        } else {
            res.setSpentPercentage(0.0);
        }

        return res;
    }

    // Budget summary — category wise breakdown
    public BudgetResponse getBudgetSummary(Long tripId) {
        verifyTripOwner(tripId);

        Budget budget = budgetRepository.findByTripId(tripId)
                .orElseThrow(() ->
                        new RuntimeException("Budget not set"));

        BudgetResponse res = BudgetResponse.fromEntity(budget);

        List<Expense> expenses = expenseRepository.findByTripId(tripId);

        // Total spent
        double totalSpent = expenses.stream()
                .mapToDouble(e -> e.getAmount() != null ? e.getAmount() : 0)
                .sum();

        res.setTotalSpent(totalSpent);
        res.setRemainingBudget(budget.getTotalAmount() - totalSpent);

        if (budget.getTotalAmount() > 0) {
            double pct = (totalSpent / budget.getTotalAmount()) * 100;
            res.setSpentPercentage(Math.round(pct * 100.0) / 100.0);
        }

        return res;
    }

    // Budget delete karo
    public void deleteBudget(Long tripId) {
        verifyTripOwner(tripId);
        Budget budget = budgetRepository.findByTripId(tripId)
                .orElseThrow(() ->
                        new RuntimeException("Budget not found"));
        budgetRepository.delete(budget);
    }
}