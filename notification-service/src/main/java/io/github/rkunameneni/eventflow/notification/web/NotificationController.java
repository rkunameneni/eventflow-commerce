package io.github.rkunameneni.eventflow.notification.web;

import io.github.rkunameneni.eventflow.notification.domain.Channel;
import io.github.rkunameneni.eventflow.notification.domain.Notification;
import io.github.rkunameneni.eventflow.notification.domain.NotificationRepository;
import io.github.rkunameneni.eventflow.support.security.CurrentUser;
import io.github.rkunameneni.eventflow.support.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List notifications", description = "Customers see their own; admins must name a customer.")
    public PageResponse<NotificationResponse> listNotifications(
            @RequestParam(required = false) String customerId,
            @PageableDefault(size = 20, sort = "sentAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        CurrentUser user = CurrentUser.from(authentication);
        String owner = user.admin() && customerId != null ? customerId : user.id();
        return PageResponse.of(notifications.findByCustomerId(owner, pageable), NotificationResponse::from);
    }

    public record NotificationResponse(UUID id, UUID orderId, Notification.Type type, Channel channel,
                                       String subject, String body, Instant sentAt) {

        static NotificationResponse from(Notification notification) {
            return new NotificationResponse(notification.getId(), notification.getOrderId(), notification.getType(),
                    notification.getChannel(), notification.getSubject(), notification.getBody(),
                    notification.getSentAt());
        }
    }
}
