package io.github.rkunameneni.eventflow.notification.application;

import io.github.rkunameneni.eventflow.notification.domain.Channel;
import io.github.rkunameneni.eventflow.notification.domain.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * E-mail channel that writes the message to the structured log instead of an SMTP relay, which
 * keeps the demo self-contained. A real implementation (SES, SendGrid) would be another
 * {@link NotificationSender} bean selected for {@link Channel#EMAIL}.
 */
@Component
public class LoggingEmailSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public void send(String customerId, Message message) {
        log.info("E-mail to customer {}: subject='{}'", customerId, message.subject());
    }
}
