package com.tripnest.backend.service;

import com.tripnest.backend.dto.ExpenseRequest;
import com.tripnest.backend.dto.ExpenseResponse;
import com.tripnest.backend.dto.ExpenseSummaryResponse;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
    }

    private Trip verifyTripOwner(Long tripId) {
        User user = getCurrentUser();
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() ->
                        new RuntimeException("Trip not found"));
        if (!trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        return trip;
    }

    // Expense add karo
    public ExpenseResponse addExpense(Long tripId,
                                      ExpenseRequest request) {
        Trip trip = verifyTripOwner(tripId);
        User user = getCurrentUser();

        Expense expense = Expense.builder()
                .description(request.getDescription())
                .amount(request.getAmount())
                .category(request.getCategory())
                .expenseDate(request.getExpenseDate())
                .receiptUrl(request.getReceiptUrl())
                .trip(trip)
                .paidBy(user)
                .build();

        return ExpenseResponse.fromEntity(
                expenseRepository.save(expense));
    }

    // Trip ki saari expenses
    public List<ExpenseResponse> getExpensesByTrip(Long tripId) {
        verifyTripOwner(tripId);
        return expenseRepository.findByTripId(tripId)
                .stream()
                .sorted((a, b) -> b.getExpenseDate()
                        .compareTo(a.getExpenseDate()))
                .map(ExpenseResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // Category se filter karo
    public List<ExpenseResponse> getExpensesByCategory(
            Long tripId, Expense.ExpenseCategory category) {
        verifyTripOwner(tripId);
        return expenseRepository.findByTripId(tripId)
                .stream()
                .filter(e -> e.getCategory() == category)
                .map(ExpenseResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // Expense update karo
    public ExpenseResponse updateExpense(Long expenseId,
                                         ExpenseRequest request) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() ->
                        new RuntimeException("Expense not found"));

        verifyTripOwner(expense.getTrip().getId());

        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setCategory(request.getCategory());
        expense.setExpenseDate(request.getExpenseDate());
        expense.setReceiptUrl(request.getReceiptUrl());

        return ExpenseResponse.fromEntity(
                expenseRepository.save(expense));
    }

    // Expense delete karo
    public void deleteExpense(Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() ->
                        new RuntimeException("Expense not found"));
        verifyTripOwner(expense.getTrip().getId());
        expenseRepository.delete(expense);
    }

    // Expense summary — category wise breakdown
    public ExpenseSummaryResponse getExpenseSummary(Long tripId) {
        Trip trip = verifyTripOwner(tripId);

        List<Expense> expenses = expenseRepository.findByTripId(tripId);

        // Total spent
        double totalSpent = expenses.stream()
                .mapToDouble(e -> e.getAmount() != null
                        ? e.getAmount() : 0)
                .sum();

        // Category wise total amount
        Map<String, Double> breakdown = new LinkedHashMap<>();
        for (Expense.ExpenseCategory cat :
                Expense.ExpenseCategory.values()) {
            double catTotal = expenses.stream()
                    .filter(e -> e.getCategory() == cat)
                    .mapToDouble(e -> e.getAmount() != null
                            ? e.getAmount() : 0)
                    .sum();
            breakdown.put(cat.name(), catTotal);
        }

        // Category wise count
        Map<String, Long> count = new LinkedHashMap<>();
        for (Expense.ExpenseCategory cat :
                Expense.ExpenseCategory.values()) {
            long catCount = expenses.stream()
                    .filter(e -> e.getCategory() == cat)
                    .count();
            count.put(cat.name(), catCount);
        }

        // Budget info
        double totalBudget = 0;
        double remaining = 0;
        double percentage = 0;

        var budgetOpt = budgetRepository.findByTripId(tripId);
        if (budgetOpt.isPresent()) {
            totalBudget = budgetOpt.get().getTotalAmount();
            remaining = totalBudget - totalSpent;
            if (totalBudget > 0) {
                percentage = Math.round(
                        (totalSpent / totalBudget) * 10000.0) / 100.0;
            }
        }

        ExpenseSummaryResponse summary = new ExpenseSummaryResponse();
        summary.setTripId(tripId);
        summary.setTripTitle(trip.getTitle());
        summary.setTotalSpent(totalSpent);
        summary.setTotalBudget(totalBudget);
        summary.setRemainingBudget(remaining);
        summary.setSpentPercentage(percentage);
        summary.setCategoryBreakdown(breakdown);
        summary.setCategoryCount(count);

        return summary;
    }
}