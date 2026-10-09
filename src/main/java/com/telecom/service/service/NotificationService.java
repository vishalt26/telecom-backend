package com.telecom.service.service;

import com.telecom.service.domain.Notification;
import com.telecom.service.domain.TicketType;
import com.telecom.service.dto.Dtos;
import com.telecom.service.dto.Dtos.NotificationResponse;
import com.telecom.service.dto.Dtos.PageResponse;
import com.telecom.service.dto.Dtos.UnreadCount;
import com.telecom.service.repo.NotificationRepository;
import com.telecom.service.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /** Keep messages free of personal data (no names, emails, phone numbers, complaint text). */
    @Transactional
    public void send(Long userId, String message, Long ticketId, TicketType ticketType) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setMessage(message);
        n.setTicketId(ticketId);
        n.setTicketType(ticketType);
        notifications.save(n);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, int page, int size) {
        return PageResponse.of(notifications
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, Dtos.pageable(page, size))
                .map(NotificationResponse::from));
    }

    @Transactional(readOnly = true)
    public UnreadCount unread(Long userId) {
        return new UnreadCount(notifications.countByUserIdAndReadFalse(userId));
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification n = notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        n.setRead(true);
        notifications.save(n);
    }

    /** Opening a ticket counts as having seen its notifications. */
    @Transactional
    public void markTicketRead(Long userId, Long ticketId) {
        notifications.markTicketRead(userId, ticketId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notifications.markAllRead(userId);
    }
}
