package com.tripnest.backend.service;

import com.tripnest.backend.entity.Trip;
import com.tripnest.backend.entity.Notification;
import com.tripnest.backend.repository.TripRepository;
import com.tripnest.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final TripRepository tripRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    // Har din subah 9 AM pe run hoga
    @Scheduled(cron = "0 0 9 * * *")
    public void sendTripReminders() {
        log.info("Running trip reminder scheduler...");

        LocalDate today = LocalDate.now();
        LocalDate threeDaysLater = today.plusDays(3);
        LocalDate oneDayLater = today.plusDays(1);

        List<Trip> allTrips = tripRepository.findAll();

        for (Trip trip : allTrips) {
            if (trip.getStartDate() == null) continue;
            if (trip.getStatus() == Trip.TripStatus.COMPLETED
                    || trip.getStatus()
                            == Trip.TripStatus.CANCELLED) {
                continue;
            }

            long daysLeft = ChronoUnit.DAYS.between(
                    today, trip.getStartDate());

            // 3 din pehle reminder
            if (daysLeft == 3 || daysLeft == 1) {
                notificationService.createNotification(
                    trip.getUser(),
                    "✈️ Your trip \"" + trip.getTitle()
                        + "\" to " + trip.getDestination()
                        + " starts in " + daysLeft + " day(s)!",
                    Notification.NotificationType.TRIP_REMINDER
                );

                // Email bhi bhejo
                emailService.sendTripReminderEmail(
                    trip.getUser().getEmail(),
                    trip.getUser().getName(),
                    trip.getTitle(),
                    trip.getDestination(),
                    trip.getStartDate().toString(),
                    daysLeft
                );

                log.info("Trip reminder sent for: {}",
                        trip.getTitle());
            }
        }
    }

    // Test ke liye — har 5 min pe (production mein comment out karo)
    // @Scheduled(fixedRate = 300000)
    // public void testReminder() {
    //     sendTripReminders();
    // }
}