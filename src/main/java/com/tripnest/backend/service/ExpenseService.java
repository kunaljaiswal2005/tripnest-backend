package com.tripnest.backend.service;

import com.tripnest.backend.dto.ExpenseRequest;
import com.tripnest.backend.dto.ExpenseResponse;
import com.tripnest.backend.dto.ExpenseSummaryResponse;
import com.tripnest.backend.dto.SplitMemberRequest;
import com.tripnest.backend.entity.Budget;
import com.tripnest.backend.entity.Expense;
import com.tripnest.backend.entity.ExpenseSplit;
import com.tripnest.backend.entity.Trip;
import com.tripnest.backend.entity.User;
import com.tripnest.backend.repository.BudgetRepository;
import com.tripnest.backend.repository.ExpenseRepository;
import com.tripnest.backend.repository.ExpenseSplitRepository;
import com.tripnest.backend.repository.TripRepository;
import com.tripnest.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;

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

    // ============================================================
    // CREATE SPLITS
    // ============================================================

    private void createSplits(Expense expense,
                               ExpenseRequest request) {

        List<SplitMemberRequest> members = request.getMembers();
        if (members == null || members.isEmpty()) return;

        int count = members.size();
        double total = expense.getAmount();
        List<ExpenseSplit> splits = new ArrayList<>();

        if (request.getSplitType()
                == Expense.SplitType.EQUAL) {

            // Base amount — floor to 2 decimals
            double base = Math.floor(
                    (total / count) * 100) / 100;
            double totalAssigned = base * (count - 1);

            // Last member ko remaining dena — rounding fix
            double lastShare = Math.round(
                    (total - totalAssigned) * 100.0) / 100.0;

            for (int i = 0; i < members.size(); i++) {
                SplitMemberRequest m = members.get(i);

                User member = userRepository
                        .findById(m.getUserId())
                        .orElseThrow(() ->
                                new RuntimeException(
                                    "User not found: "
                                    + m.getUserId()));

                double share = (i == members.size() - 1)
                        ? lastShare : base;

                // Agar ye member hi payer hai
                double paid = member.getId().equals(
                        expense.getPaidBy().getId())
                        ? expense.getAmount() : 0.0;

                ExpenseSplit split = ExpenseSplit.builder()
                        .expense(expense)
                        .user(member)
                        .shareAmount(share)
                        .paidAmount(paid)
                        .isSettled(false)
                        .build();

                splits.add(split);
            }

        } else if (request.getSplitType()
                == Expense.SplitType.CUSTOM) {

            // Validate — custom amounts ka sum == total
            double customTotal = members.stream()
                    .mapToDouble(m -> m.getCustomAmount() != null
                            ? m.getCustomAmount() : 0)
                    .sum();

            double diff = Math.abs(customTotal - total);
            if (diff > 0.02) {
                throw new RuntimeException(
                    "Custom amounts sum (" + customTotal
                    + ") does not match expense amount ("
                    + total + ")");
            }

            for (SplitMemberRequest m : members) {
                if (m.getCustomAmount() == null) {
                    throw new RuntimeException(
                        "Custom amount required for all members");
                }

                User member = userRepository
                        .findById(m.getUserId())
                        .orElseThrow(() ->
                                new RuntimeException(
                                    "User not found: "
                                    + m.getUserId()));

                double paid = member.getId().equals(
                        expense.getPaidBy().getId())
                        ? expense.getAmount() : 0.0;

                ExpenseSplit split = ExpenseSplit.builder()
                        .expense(expense)
                        .user(member)
                        .shareAmount(m.getCustomAmount())
                        .paidAmount(paid)
                        .isSettled(false)
                        .build();

                splits.add(split);
            }
        }

        expenseSplitRepository.saveAll(splits);
    }

    // ============================================================
    // ADD EXPENSE
    // ============================================================

    @Transactional
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
                .isShared(request.getIsShared() != null
                        ? request.getIsShared() : false)
                .splitType(request.getSplitType())
                .trip(trip)
                .paidBy(user)
                .build();

        Expense saved = expenseRepository.save(expense);

        // Shared expense — splits banao
        if (Boolean.TRUE.equals(request.getIsShared())
                && request.getSplitType() != null
                && request.getMembers() != null
                && !request.getMembers().isEmpty()) {
            createSplits(saved, request);
        }

        // Fresh load — splits ke saath
        return ExpenseResponse.fromEntity(
                expenseRepository.findById(saved.getId())
                        .orElseThrow());
    }

    // ============================================================
    // GET ALL EXPENSES BY TRIP
    // ============================================================

    public List<ExpenseResponse> getExpensesByTrip(Long tripId) {
        verifyTripOwner(tripId);
        return expenseRepository.findByTripId(tripId)
                .stream()
                .sorted((a, b) -> b.getExpenseDate()
                        .compareTo(a.getExpenseDate()))
                .map(ExpenseResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // ============================================================
    // GET BY CATEGORY
    // ============================================================

    public List<ExpenseResponse> getExpensesByCategory(
            Long tripId,
            Expense.ExpenseCategory category) {
        verifyTripOwner(tripId);
        return expenseRepository.findByTripId(tripId)
                .stream()
                .filter(e -> e.getCategory() == category)
                .map(ExpenseResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // ============================================================
    // UPDATE EXPENSE
    // ============================================================

    @Transactional
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
        expense.setIsShared(request.getIsShared() != null
                ? request.getIsShared() : false);
        expense.setSplitType(request.getSplitType());

        expenseRepository.save(expense);

        // Purane splits delete karo — naye banao
        expenseSplitRepository.deleteByExpenseId(expenseId);

        if (Boolean.TRUE.equals(request.getIsShared())
                && request.getSplitType() != null
                && request.getMembers() != null
                && !request.getMembers().isEmpty()) {
            createSplits(expense, request);
        }

        return ExpenseResponse.fromEntity(
                expenseRepository.findById(expenseId)
                        .orElseThrow());
    }

    // ============================================================
    // DELETE EXPENSE
    // ============================================================

    @Transactional
    public void deleteExpense(Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() ->
                        new RuntimeException("Expense not found"));

        verifyTripOwner(expense.getTrip().getId());

        // Splits pehle delete karo
        expenseSplitRepository.deleteByExpenseId(expenseId);

        expenseRepository.delete(expense);
    }

    // ============================================================
    // EXPENSE SUMMARY
    // ============================================================

    public ExpenseSummaryResponse getExpenseSummary(Long tripId) {
        Trip trip = verifyTripOwner(tripId);

        List<Expense> expenses =
                expenseRepository.findByTripId(tripId);

        double totalSpent = expenses.stream()
                .mapToDouble(e -> e.getAmount() != null
                        ? e.getAmount() : 0)
                .sum();

        // Category wise total
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
            Budget budget = budgetOpt.get();
            totalBudget = budget.getTotalAmount();
            remaining = totalBudget - totalSpent;
            if (totalBudget > 0) {
                percentage = Math.round(
                    (totalSpent / totalBudget)
                    * 10000.0) / 100.0;
            }
        }

        ExpenseSummaryResponse summary =
                new ExpenseSummaryResponse();
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