package io.github.rkunameneni.eventflow.notification;

import io.github.rkunameneni.eventflow.support.EventflowApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NotificationServiceApplication {

    public static final String SERVICE_NAME = "notification-service";

    public static void main(String[] args) {
        EventflowApplication.run(NotificationServiceApplication.class, SERVICE_NAME, args);
    }
}
