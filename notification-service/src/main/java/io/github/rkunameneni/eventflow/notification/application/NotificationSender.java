package io.github.rkunameneni.eventflow.notification.application;

import io.github.rkunameneni.eventflow.notification.domain.Channel;
import io.github.rkunameneni.eventflow.notification.domain.Message;

/** Strategy for one delivery channel (e-mail, SMS, push, ...). */
public interface NotificationSender {

    Channel channel();

    void send(String customerId, Message message);
}
