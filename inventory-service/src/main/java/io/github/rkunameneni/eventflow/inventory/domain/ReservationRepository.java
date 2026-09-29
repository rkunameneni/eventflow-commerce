package io.github.rkunameneni.eventflow.inventory.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByOrderIdAndStatus(UUID orderId, Reservation.Status status);

    List<Reservation> findByOrderId(UUID orderId);
}
