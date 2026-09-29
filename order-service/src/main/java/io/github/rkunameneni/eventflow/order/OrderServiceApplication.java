package io.github.rkunameneni.eventflow.order;

import io.github.rkunameneni.eventflow.support.EventflowApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderServiceApplication {

    public static final String SERVICE_NAME = "order-service";

    public static void main(String[] args) {
        EventflowApplication.run(OrderServiceApplication.class, SERVICE_NAME, args);
    }
}
