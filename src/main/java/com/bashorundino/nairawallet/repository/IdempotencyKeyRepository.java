package com.bashorundino.nairawallet.repository;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.entity.IdempotencyKey;
import com.bashorundino.nairawallet.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByUser_IdAndOperationTypeAndIdempotencyKey(
            Long userid,
            TransactionType operationType,
            String IdempotencyKey);

    @Query("""
            SELECT ik
            FROM IdempotencyKey ik
            LEFT JOIN FETCH ik.transaction
            WHERE ik.user.id = :userId
              AND ik.operationType = :operationType
              AND ik.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyKey> findExistingWithTransaction(
            @Param("userId") Long userId,
            @Param("operationType") TransactionType operationType,
            @Param("idempotencyKey") String idempotencyKey
    );

}
