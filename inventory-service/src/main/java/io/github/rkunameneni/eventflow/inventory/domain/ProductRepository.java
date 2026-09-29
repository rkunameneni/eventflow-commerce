package io.github.rkunameneni.eventflow.inventory.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, String> {

    /**
     * Locks the rows of an order's SKUs for the reservation. Rows are always locked in SKU order,
     * so two orders touching the same products cannot deadlock each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.sku in :skus order by p.sku")
    List<Product> lockAllBySkuIn(@Param("skus") Collection<String> skus);
}
