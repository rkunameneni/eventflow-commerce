package io.github.rkunameneni.eventflow.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecord.Key> {
}
