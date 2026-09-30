package com.tripnest.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripnest.backend.dto.ExpenseRequest;
import com.tripnest.backend.dto.ExpenseResponse;
import com.tripnest.backend.dto.ExpenseSummaryResponse;
import com.tripnest.backend.dto.SplitMemberRequest;
import com.tripnest.backend.entity.Budget;
import com.tripnest.backend.entity.Expense;
import com.tripnest.backend.entity.ExpenseSplit;
import com.tripnest.backend.entity.Notification;
import com.tripnest.backend.entity.Trip;
import com.tripnest.backend.entity.User;
import com.tripnest.backend.repository.BudgetRepository;
import com.tripnest.backend.repository.ExpenseRepository;
import com.tripnest.backend.repository.ExpenseSplitRepository;
import com.tripnest.backend.repository.TripRepository;
import com.tripnest.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;
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

            double base = Math.floor(
                    (total / count) * 100) / 100;
            double totalAssigned = base * (count - 1);
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
                double paid = member.getId().equals(
                        expense.getPaidBy().getId())
                        ? expense.getAmount() : 0.0;

                splits.add(ExpenseSplit.builder()
                        .expense(expense)
                        .user(member)
                        .shareAmount(share)
                        .paidAmount(paid)
                        .isSettled(false)
                        .build());
            }

        } else if (request.getSplitType()
                == Expense.SplitType.CUSTOM) {

            double customTotal = members.stream()
                    .mapToDouble(m -> m.getCustomAmount() != null
                            ? m.getCustomAmount() : 0)
                    .sum();

            if (Math.abs(customTotal - total) > 0.02) {
                throw new RuntimeException(
                    "Custom amounts sum (" + customTotal
                    + ") does not match expense ("
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

                splits.add(ExpenseSplit.builder()
                        .expense(expense)
                        .user(member)
                        .shareAmount(m.getCustomAmount())
                        .paidAmount(paid)
                        .isSettled(false)
                        .build());
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

        // Shared expense
        if (Boolean.TRUE.equals(request.getIsShared())
                && request.getSplitType() != null
                && request.getMembers() != null
                && !request.getMembers().isEmpty()) {

            createSplits(saved, request);

            // ✅ Notify each split member
            for (SplitMemberRequest m : request.getMembers()) {
                userRepository.findById(m.getUserId())
                        .ifPresent(member -> {
                    if (!member.getId().equals(user.getId())) {

                        double share = request.getSplitType()
                                == Expense.SplitType.EQUAL
                                ? Math.round((saved.getAmount()
                                    / request.getMembers().size())
                                    * 100.0) / 100.0
                                : (m.getCustomAmount() != null
                                    ? m.getCustomAmount() : 0);

                        notificationService.createNotification(
                                member,
                                user.getName()
                                    + " added shared expense: \""
                                    + saved.getDescription()
                                    + "\" — your share: ₹"
                                    + share,
                                Notification.NotificationType
                                        .BUDGET_ALERT,
                                saved.getTrip().getId(),
                                Notification.ReferenceType.TRIP
                        );
                    }
                });
            }
        }

        return ExpenseResponse.fromEntity(
                expenseRepository.findById(saved.getId())
                        .orElseThrow());
    }

    // ============================================================
    // GET ALL BY TRIP
    // ============================================================

    public List<ExpenseResponse> getExpensesByTrip(
            Long tripId) {
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
        User user = getCurrentUser();

        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setCategory(request.getCategory());
        expense.setExpenseDate(request.getExpenseDate());
        expense.setReceiptUrl(request.getReceiptUrl());
        expense.setIsShared(request.getIsShared() != null
                ? request.getIsShared() : false);
        expense.setSplitType(request.getSplitType());

        expenseRepository.save(expense);

        // Purane splits delete — naye banao
        expenseSplitRepository.deleteByExpenseId(expenseId);

        if (Boolean.TRUE.equals(request.getIsShared())
                && request.getSplitType() != null
                && request.getMembers() != null
                && !request.getMembers().isEmpty()) {

            createSplits(expense, request);

            // ✅ Notify members on update
            for (SplitMemberRequest m : request.getMembers()) {
                userRepository.findById(m.getUserId())
                        .ifPresent(member -> {
                    if (!member.getId().equals(user.getId())) {
                        notificationService.createNotification(
                                member,
                                user.getName()
                                    + " updated shared expense: \""
                                    + expense.getDescription()
                                    + "\"",
                                Notification.NotificationType
                                        .BUDGET_ALERT,
                                expense.getTrip().getId(),
                                Notification.ReferenceType.TRIP
                        );
                    }
                });
            }
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

        expenseSplitRepository.deleteByExpenseId(expenseId);
        expenseRepository.delete(expense);
    }

    // ============================================================
    // EXPENSE SUMMARY
    // ============================================================

    public ExpenseSummaryResponse getExpenseSummary(
            Long tripId) {
        Trip trip = verifyTripOwner(tripId);

        List<Expense> expenses =
                expenseRepository.findByTripId(tripId);

        double totalSpent = expenses.stream()
                .mapToDouble(e -> e.getAmount() != null
                        ? e.getAmount() : 0)
                .sum();

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

        Map<String, Long> count = new LinkedHashMap<>();
        for (Expense.ExpenseCategory cat :
                Expense.ExpenseCategory.values()) {
            long catCount = expenses.stream()
                    .filter(e -> e.getCategory() == cat)
                    .count();
            count.put(cat.name(), catCount);
        }

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