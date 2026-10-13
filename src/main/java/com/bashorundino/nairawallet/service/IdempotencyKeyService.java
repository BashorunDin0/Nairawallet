package com.bashorundino.nairawallet.service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.entity.IdempotencyKey;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.enums.TransactionType;

public interface IdempotencyKeyService {

    IdempotencyKey findExisting(Long userId,
                                TransactionType operationType,
                                String IdempotencyKey);

    void validateSameRequest(IdempotencyKey existing,
                             String requestHash);

    IdempotencyKey create(
            String key,
            User user,
            TransactionType operationType,
            String requestHash
    );

    void markCompleted(
            IdempotencyKey idempotencyKey,
            Transaction transaction);

}
