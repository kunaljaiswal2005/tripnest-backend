package com.tripnest.backend.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.tripnest.backend.dto.DocumentResponse;
import com.tripnest.backend.entity.Notification;
import com.tripnest.backend.entity.Trip;
import com.tripnest.backend.entity.TripDocument;
import com.tripnest.backend.entity.User;
import com.tripnest.backend.repository.TripDocumentRepository;
import com.tripnest.backend.repository.TripRepository;
import com.tripnest.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final Cloudinary cloudinary;
    private final TripDocumentRepository documentRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // ============================================================
    // CONSTANTS
    // ============================================================

    private static final long MAX_FILE_SIZE =
            10 * 1024 * 1024; // 10MB

    private static final List<String> ALLOWED_TYPES =
            Arrays.asList(
                    "image/jpeg", "image/jpg", "image/png",
                    "image/gif", "image/webp",
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument"
                            + ".wordprocessingml.document",
                    "text/plain"
            );

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

    private Trip verifyTripAccess(Long tripId) {
        tripRepository.findById(tripId)
                .orElseThrow(() ->
                        new RuntimeException("Trip not found"));
        return tripRepository.findById(tripId).get();
    }

    // ============================================================
    // VALIDATE FILE
    // ============================================================

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException(
                    "File size exceeds 10MB limit. "
                    + "Current size: "
                    + String.format("%.1f MB",
                            file.getSize() / 1048576.0));
        }

        String contentType = file.getContentType();
        if (contentType == null
                || !ALLOWED_TYPES.contains(contentType)) {
            throw new RuntimeException(
                    "File type not allowed: " + contentType
                    + ". Allowed: images, PDF, Word, text");
        }
    }

    // ============================================================
    // UPLOAD FILE
    // ============================================================

    @SuppressWarnings("unchecked")
    public DocumentResponse uploadDocument(
            Long tripId,
            MultipartFile file,
            TripDocument.DocumentType documentType,
            String description) throws IOException {

        // Validate
        validateFile(file);

        Trip trip = verifyTripAccess(tripId);
        User user = getCurrentUser();

        // Cloudinary folder structure
        String folder = "tripnest/trip_" + tripId;
        String publicId = folder + "/"
                + UUID.randomUUID().toString();

        // Upload to Cloudinary
        String contentType = file.getContentType();
        boolean isImage = contentType != null
                && contentType.startsWith("image/");

        Map<String, Object> options = ObjectUtils.asMap(
                "public_id",    publicId,
                "resource_type",
                        isImage ? "image" : "raw",
                "folder",       folder
        );

        Map<String, Object> uploadResult =
                cloudinary.uploader().upload(
                        file.getBytes(), options);

        String fileUrl = (String) uploadResult.get("secure_url");
        String cloudinaryPublicId =
                (String) uploadResult.get("public_id");

        log.info("File uploaded to Cloudinary: {}",
                cloudinaryPublicId);

        // Save metadata to DB
        TripDocument document = TripDocument.builder()
                .fileName(cloudinaryPublicId
                        .substring(cloudinaryPublicId
                                .lastIndexOf('/') + 1))
                .originalFileName(file.getOriginalFilename())
                .cloudinaryPublicId(cloudinaryPublicId)
                .fileUrl(fileUrl)
                .fileType(contentType)
                .fileSize(file.getSize())
                .documentType(documentType != null
                        ? documentType
                        : TripDocument.DocumentType.OTHER)
                .description(description)
                .trip(trip)
                .uploadedBy(user)
                .build();

        TripDocument saved = documentRepository.save(document);

        // Notification
        notificationService.createNotification(
                user,
                "Document uploaded: \""
                        + file.getOriginalFilename()
                        + "\" for trip \""
                        + trip.getTitle() + "\"",
                Notification.NotificationType.SYSTEM,
                trip.getId(),
                Notification.ReferenceType.TRIP
        );

        return DocumentResponse.fromEntity(saved);
    }

    // ============================================================
    // GET DOCUMENTS BY TRIP
    // ============================================================

    public List<DocumentResponse> getDocumentsByTrip(
            Long tripId) {
        verifyTripAccess(tripId);
        return documentRepository.findByTripId(tripId)
                .stream()
                .map(DocumentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // ============================================================
    // GET BY TYPE
    // ============================================================

    public List<DocumentResponse> getDocumentsByType(
            Long tripId,
            TripDocument.DocumentType type) {
        verifyTripAccess(tripId);
        return documentRepository
                .findByTripIdAndDocumentType(tripId, type)
                .stream()
                .map(DocumentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // ============================================================
    // GET PHOTOS ONLY
    // ============================================================

    public List<DocumentResponse> getPhotosByTrip(
            Long tripId) {
        return getDocumentsByType(
                tripId, TripDocument.DocumentType.PHOTO);
    }

    // ============================================================
    // DELETE
    // ============================================================

    @SuppressWarnings("unchecked")
    public void deleteDocument(Long documentId)
            throws IOException {

        TripDocument document = documentRepository
                .findById(documentId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Document not found"));

        User currentUser = getCurrentUser();
        Trip trip = document.getTrip();

        // Only uploader or trip owner can delete
        boolean isUploader = document.getUploadedBy()
                .getId().equals(currentUser.getId());
        boolean isTripOwner = trip.getUser()
                .getId().equals(currentUser.getId());

        if (!isUploader && !isTripOwner) {
            throw new RuntimeException(
                    "Only uploader or trip owner can delete");
        }

        // Delete from Cloudinary
        boolean isImage = document.getFileType() != null
                && document.getFileType().startsWith("image/");

        cloudinary.uploader().destroy(
                document.getCloudinaryPublicId(),
                ObjectUtils.asMap(
                        "resource_type",
                        isImage ? "image" : "raw")
        );

        log.info("File deleted from Cloudinary: {}",
                document.getCloudinaryPublicId());

        // Delete from DB
        documentRepository.delete(document);
    }

    // ============================================================
    // GET SINGLE DOCUMENT
    // ============================================================

    public DocumentResponse getDocumentById(Long documentId) {
        TripDocument document = documentRepository
                .findById(documentId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Document not found"));
        return DocumentResponse.fromEntity(document);
    }
}