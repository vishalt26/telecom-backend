package com.telecom.service.web;

import com.telecom.service.dto.Dtos.NotificationResponse;
import com.telecom.service.dto.Dtos.PageResponse;
import com.telecom.service.dto.Dtos.UnreadCount;
import com.telecom.service.security.CurrentUser;
import com.telecom.service.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService notifications, CurrentUser currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return notifications.list(currentUser.get().getId(), page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCount unread() {
        return notifications.unread(currentUser.get().getId());
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id) {
        notifications.markRead(currentUser.get().getId(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead() {
        notifications.markAllRead(currentUser.get().getId());
    }
}
