package io.github.rkunameneni.eventflow.payment;

import io.github.rkunameneni.eventflow.support.EventflowApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentServiceApplication {

    public static final String SERVICE_NAME = "payment-service";

    public static void main(String[] args) {
        EventflowApplication.run(PaymentServiceApplication.class, SERVICE_NAME, args);
    }
}
