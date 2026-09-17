package com.example.nairawallet.Service;

import com.example.nairawallet.Dto.Request.DepositRequest;
import com.example.nairawallet.Dto.Request.WithdrawRequest;
import com.example.nairawallet.Dto.Response.TransactionResponse;
import com.example.nairawallet.Dto.Request.TransferRequest;
import com.example.nairawallet.Entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

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
