package io.github.rkunameneni.eventflow.inventory;

import io.github.rkunameneni.eventflow.support.EventflowApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InventoryServiceApplication {

    public static final String SERVICE_NAME = "inventory-service";

    public static void main(String[] args) {
        EventflowApplication.run(InventoryServiceApplication.class, SERVICE_NAME, args);
    }
}
