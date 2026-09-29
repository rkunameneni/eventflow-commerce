package io.github.rkunameneni.eventflow.order.web;

import io.github.rkunameneni.eventflow.order.domain.Order;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "A new order. Unit prices are the prices quoted to the customer; "
        + "inventory-service rejects the order if the catalogue price has changed since.")
public record PlaceOrderRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 code such as USD")
        @Schema(example = "USD") String currency,
        @NotEmpty @Size(max = 50) List<@Valid Line> lines) {

    public List<Order.LineItem> toLineItems() {
        return lines.stream().map(line -> new Order.LineItem(line.sku(), line.quantity(), line.unitPrice())).toList();
    }

    public record Line(
            @NotBlank @Size(max = 64) @Schema(example = "SKU-KEYBOARD") String sku,
            @Min(1) @Max(1000) @Schema(example = "2") int quantity,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) @Schema(example = "79.00")
            BigDecimal unitPrice) {
    }
}
