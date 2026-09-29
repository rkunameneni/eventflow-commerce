package io.github.rkunameneni.eventflow.order.web;

import io.github.rkunameneni.eventflow.order.application.OrderPlacementService;
import io.github.rkunameneni.eventflow.order.application.OrderQueryService;
import io.github.rkunameneni.eventflow.order.application.PlaceOrderCommand;
import io.github.rkunameneni.eventflow.order.application.PlacementResult;
import io.github.rkunameneni.eventflow.order.domain.OrderStatus;
import io.github.rkunameneni.eventflow.support.security.CurrentUser;
import io.github.rkunameneni.eventflow.support.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
public class OrderController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final OrderPlacementService placement;
    private final OrderQueryService queries;

    public OrderController(OrderPlacementService placement, OrderQueryService queries) {
        this.placement = placement;
        this.queries = queries;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Place an order", description = "Starts the fulfilment saga. Retrying with the same "
            + "Idempotency-Key and body returns the original order instead of creating a new one.")
    @ApiResponse(responseCode = "201", description = "Order accepted; fulfilment continues asynchronously")
    @ApiResponse(responseCode = "200", description = "Replay of an earlier request with the same Idempotency-Key")
    @ApiResponse(responseCode = "422", description = "Idempotency-Key reused with a different body, or duplicate SKUs")
    public ResponseEntity<OrderResponse> placeOrder(
            @RequestHeader(IDEMPOTENCY_KEY)
            @Pattern(regexp = "[A-Za-z0-9-]{8,64}", message = "must be 8-64 letters, digits or dashes")
            @Parameter(description = "Client-generated key, e.g. a UUID") String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request,
            Authentication authentication) {
        PlacementResult result = placement.place(new PlaceOrderCommand(
                authentication.getName(), idempotencyKey, request.currency(), request.toLineItems()));
        OrderResponse body = OrderResponse.from(result.order());
        if (result.replayed()) {
            return ResponseEntity.ok().header(IDEMPOTENT_REPLAYED, "true").body(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/orders/" + body.id())).body(body);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get an order with its lines and current saga status")
    public OrderResponse getOrder(@PathVariable UUID orderId, Authentication authentication) {
        return OrderResponse.from(queries.get(orderId, CurrentUser.from(authentication)));
    }

    @GetMapping
    @Operation(summary = "List orders", description = "Customers see their own orders; admins may filter by customer.")
    public PageResponse<OrderSummary> listOrders(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return PageResponse.of(
                queries.search(CurrentUser.from(authentication), customerId, status, pageable), OrderSummary::from);
    }
}
