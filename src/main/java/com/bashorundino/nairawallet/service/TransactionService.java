package com.bashorundino.nairawallet.service;

import com.bashorundino.nairawallet.dto.request.DepositRequest;
import com.bashorundino.nairawallet.dto.request.WithdrawRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.dto.request.TransferRequest;
import org.springframework.data.domain.Page;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

public interface TransactionService {

    TransactionResponse deposit(DepositRequest request, String idempotencyKey);

    TransactionResponse withdrawal(WithdrawRequest request, String idempotencyKey);

    TransactionResponse transfer(TransferRequest request, String idempotencyKey);

    Page<TransactionResponse> getTransactions(Long walletId, int page, int size);

}
