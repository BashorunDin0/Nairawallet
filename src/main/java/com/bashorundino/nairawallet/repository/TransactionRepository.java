package com.bashorundino.nairawallet.repository;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */


import com.bashorundino.nairawallet.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTxReference(
            String reference);

    Page<Transaction> findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
            Long sourceWalletId,
            Long destinationWalletId,
            Pageable pageable
    );
}
