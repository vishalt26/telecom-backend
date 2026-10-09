package com.telecom.service.dto;

import com.telecom.service.domain.*;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Request/response shapes for the REST API. See docs/api-contract.md. */
public final class Dtos {

    private Dtos() {}

    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    public static final String PASSWORD_MESSAGE = "must contain at least one letter and one digit";
    public static final String PHONE_PATTERN = "^\\+?[0-9]{7,15}$";
    public static final String PHONE_MESSAGE = "must be 7-15 digits with an optional leading +";

    // ---- auth / users ----

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 100) String name,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE) String phone,
            @NotBlank @Size(min = 8, max = 64) @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE) String password) {}

    public record LoginRequest(
            @NotBlank @Size(max = 150) String emailOrPhone,
            @NotBlank @Size(max = 64) String password) {}

    public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {}

    public record UserResponse(Long id, String name, String email, String phone, Role role, boolean enabled, Instant createdAt) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(), u.isEnabled(), u.getCreatedAt());
        }
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(min = 2, max = 100) String name,
            @NotBlank @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE) String phone) {}

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 64) String currentPassword,
            @NotBlank @Size(min = 8, max = 64) @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE) String newPassword) {}

    public record AdminUpdateUserRequest(
            @NotBlank @Size(min = 2, max = 100) String name,
            @NotBlank @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE) String phone,
            Boolean enabled,
            @Size(min = 8, max = 64) @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE) String newPassword) {}

    // ---- tickets ----

    public record TicketRequest(
            @NotNull Category category,
            @NotBlank @Size(min = 3, max = 150) String subject,
            @NotBlank @Size(min = 10, max = 4000) String description,
            Priority priority,
            @FutureOrPresent LocalDate preferredDate) {}

    public record StatusRequest(@NotNull TicketStatus status, @Size(max = 500) String note) {}

    public record AssignRequest(@NotNull Long employeeId) {}

    public record UserSummary(Long id, String name, String email, String phone) {}

    public record HistoryEntry(TicketStatus fromStatus, TicketStatus toStatus, String note, String changedByName, Instant createdAt) {
        public static HistoryEntry from(TicketHistory h) {
            return new HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getNote(), h.getChangedByName(), h.getCreatedAt());
        }
    }

    public record TicketResponse(
            Long id, TicketType type, Category category, String subject, String description,
            Priority priority, TicketStatus status, LocalDate preferredDate,
            UserSummary customer, UserSummary assignee,
            Instant createdAt, Instant updatedAt, Instant resolvedAt,
            List<HistoryEntry> history) {}

    // ---- notifications ----

    public record NotificationResponse(Long id, String message, Long ticketId, TicketType ticketType, boolean read, Instant createdAt) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getMessage(), n.getTicketId(), n.getTicketType(), n.isRead(), n.getCreatedAt());
        }
    }

    public record UnreadCount(long unread) {}

    // ---- reports ----

    public record SummaryReport(
            long total, long openCount, long resolvedCount, long createdLast30Days,
            Double avgResolutionHours,
            Map<String, Long> byStatus, Map<String, Long> byCategory, Map<String, Long> byType) {}

    public record TrendPoint(LocalDate date, long created, long resolved) {}

    // ---- paging ----

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }

    public static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }
}
