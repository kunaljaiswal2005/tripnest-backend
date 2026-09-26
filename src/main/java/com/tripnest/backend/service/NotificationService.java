package com.tripnest.backend.service;

import com.tripnest.backend.dto.NotificationRequest;
import com.tripnest.backend.dto.NotificationResponse;
import com.tripnest.backend.entity.Notification;
import com.tripnest.backend.entity.User;
import com.tripnest.backend.repository.NotificationRepository;
import com.tripnest.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
    }

    // Apni saari notifications
    public List<NotificationResponse> getMyNotifications() {
        User user = getCurrentUser();
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // Sirf unread notifications
    public List<NotificationResponse> getUnreadNotifications() {
        User user = getCurrentUser();
        return notificationRepository
                .findByUserIdAndIsReadOrderByCreatedAtDesc(
                        user.getId(), false)
                .stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // Unread count
    public long getUnreadCount() {
        User user = getCurrentUser();
        return notificationRepository
                .countByUserIdAndIsRead(user.getId(), false);
    }

    // Ek notification read mark karo
    public NotificationResponse markAsRead(Long notificationId) {
        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() ->
                        new RuntimeException("Notification not found"));

        User user = getCurrentUser();
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        notification.setIsRead(true);
        return NotificationResponse.fromEntity(
                notificationRepository.save(notification));
    }

    // Saari notifications read mark karo
    public void markAllAsRead() {
        User user = getCurrentUser();
        List<Notification> unread = notificationRepository
                .findByUserIdAndIsReadOrderByCreatedAtDesc(
                        user.getId(), false);

        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
    }

    // Notification delete karo
    public void deleteNotification(Long notificationId) {
        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() ->
                        new RuntimeException("Notification not found"));

        User user = getCurrentUser();
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        notificationRepository.delete(notification);
    }

    // Internal use — doosri services se call hongi
    public void createNotification(User user,
                                    String message,
                                    Notification.NotificationType type) {
        Notification notification = Notification.builder()
                .message(message)
                .notificationType(type)
                .isRead(false)
                .user(user)
                .build();
        notificationRepository.save(notification);
    }

    // Manual notification create karo
    public NotificationResponse createManualNotification(
            NotificationRequest request) {
        User user = getCurrentUser();
        Notification notification = Notification.builder()
                .message(request.getMessage())
                .notificationType(request.getNotificationType())
                .isRead(false)
                .user(user)
                .build();
        return NotificationResponse.fromEntity(
                notificationRepository.save(notification));
    }
}