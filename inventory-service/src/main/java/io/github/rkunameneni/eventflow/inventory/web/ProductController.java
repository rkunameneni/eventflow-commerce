package io.github.rkunameneni.eventflow.inventory.web;

import io.github.rkunameneni.eventflow.inventory.application.CatalogService;
import io.github.rkunameneni.eventflow.inventory.application.ProductView;
import io.github.rkunameneni.eventflow.inventory.application.StockLevel;
import io.github.rkunameneni.eventflow.support.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Catalogue")
public class ProductController {

    private final CatalogService catalog;

    public ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(summary = "Browse the catalogue (public)")
    public PageResponse<ProductView> listProducts(@PageableDefault(size = 20, sort = "sku") Pageable pageable) {
        return PageResponse.of(catalog.products(pageable), view -> view);
    }

    @GetMapping("/{sku}")
    @Operation(summary = "Get a product (public, served from Redis when cached)")
    public ProductView getProduct(@PathVariable String sku) {
        return catalog.product(sku);
    }

    @GetMapping("/{sku}/stock")
    @Operation(summary = "Current stock level (cached briefly)")
    public StockLevel getStock(@PathVariable String sku) {
        return catalog.availability(sku);
    }

    @PutMapping("/{sku}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create or update a product and set its sellable stock")
    public ProductView upsertProduct(
            @PathVariable @Pattern(regexp = "[A-Z0-9-]{3,64}") String sku,
            @Valid @RequestBody UpsertProductRequest request) {
        return catalog.upsert(sku, request.name(), request.unitPrice(), request.currency(), request.availableQuantity());
    }

    public record UpsertProductRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @Min(0) @Max(1_000_000) int availableQuantity) {
    }
}
