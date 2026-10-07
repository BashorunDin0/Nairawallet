package com.bashorundino.nairawallet.service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.dto.request.DepositRequest;
import com.bashorundino.nairawallet.dto.request.TransferRequest;
import com.bashorundino.nairawallet.dto.request.WithdrawRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import com.bashorundino.nairawallet.service.transaction.DepositService;
import com.bashorundino.nairawallet.service.transaction.TransferService;
import com.bashorundino.nairawallet.service.transaction.WithdrawalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImp implements TransactionService {

    private final DepositService depositService;
    private final WithdrawalService withdrawalService;
    private final TransferService transferService;
    private final WalletService walletService;
    private final CurrentUserService currentUserService;

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    @Override
    public TransactionResponse deposit(
            DepositRequest request,
            String idempotencyKey)
    {
        return depositService.deposit(request, idempotencyKey);

    }

    @Override
    public TransactionResponse withdrawal(
            WithdrawRequest request, String idempotencyKey)
    {
        return withdrawalService.withdrawal(request, idempotencyKey);
    }

    @Override
    public TransactionResponse transfer(
            TransferRequest request, String idempotencyKey)
    {
        return transferService.transfer(request, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(
            Long walletId,
            int page,
            int size) {

        Wallet wallet = walletService.findById(walletId);

        String currentUserEmail = currentUserService.getCurrentUserEmail();

        if (!wallet.getUser().getEmail().equals(currentUserEmail)) {
            throw new AccessDeniedException(
                    "You are not allowed to access this wallet"
            );
        }

        Pageable pageable = PageRequest.of(page, size);

        Page<Transaction> transactions = transactionRepository.
                findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        walletId,
                        walletId,
                        pageable);

        return transactions.map(transaction ->
                transactionMapper.mapToResponse(transaction, walletId));
    }
}
