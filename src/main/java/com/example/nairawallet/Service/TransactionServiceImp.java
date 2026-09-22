package com.example.nairawallet.Service;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.example.nairawallet.Dto.Request.DepositRequest;
import com.example.nairawallet.Dto.Request.WithdrawRequest;
import com.example.nairawallet.Dto.Response.TransactionResponse;
import com.example.nairawallet.Dto.Request.TransferRequest;
import com.example.nairawallet.Entity.LedgerEntry;
import com.example.nairawallet.Entity.Transaction;
import com.example.nairawallet.Entity.Wallet;
import com.example.nairawallet.Enum.LedgerEntryType;
import com.example.nairawallet.Enum.TransactionStatus;
import com.example.nairawallet.Enum.TransactionType;
import com.example.nairawallet.Enum.WalletStatus;
import com.example.nairawallet.Exception.InactiveWalletException;
import com.example.nairawallet.Exception.InsufficientFundsException;
import com.example.nairawallet.Mapper.TransactionMapper;
import com.example.nairawallet.Repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImp implements TransactionService {

    private final WalletService walletService;
    private final LedgerEntryService ledgerEntryService;
    private final IdempotencyKeyService idempotencyKeyService;

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    @Transactional
    public TransactionResponse deposit(DepositRequest request, String idempotencyKey) {
        log.info("Deposit request received. Wallet = {}, Amount = {}",
                request.walletId(), request.amount()
        );
        idempotencyKeyService.validate(idempotencyKey);
        Wallet wallet = walletService.findById(request.walletId());
        validateWallet(wallet);

        BigDecimal balanceBefore = wallet.getBalance();
        wallet.credit(request.amount());
        BigDecimal balanceAfter = wallet.getBalance();

        Transaction transaction = createTransaction(
                wallet,
                TransactionType.DEPOSIT,
                request.amount(),
                "Wallet Deposit"

        );

        ledgerEntryService.createEntry(
                wallet,
                transaction,
                request.amount(),
                balanceBefore,
                balanceAfter,
                LedgerEntryType.CREDIT,
                "Wallet Deposit"

        );
        markSuccessful(transaction);
        idempotencyKeyService.save(idempotencyKey);
        log.info("Deposit successful. reference = {}", transaction.getTxReference());

        return transactionMapper.mapToResponse(transaction);


    }
    @Transactional
    public TransactionResponse withdrawal(WithdrawRequest request, String idempotencyKey) {
        idempotencyKeyService.validate(idempotencyKey);
        Wallet wallet = walletService.findById(request.walletId());
        validateWallet(wallet);

        BigDecimal balanceBefore = wallet.getBalance();
        wallet.debit(request.amount());
        BigDecimal balanceAfter = wallet.getBalance();

        Transaction transaction = createTransaction(
                wallet,
                TransactionType.WITHDRAWAL,
                request.amount(),
                "Wallet Withdrawal"
        );

        ledgerEntryService.createEntry(
                wallet,
                transaction,
                request.amount(),
                balanceBefore,
                balanceAfter,
                LedgerEntryType.DEBIT,
                "Wallet Withdrawal"
        );
        markSuccessful(transaction);
        idempotencyKeyService.save(idempotencyKey);
        log.info("Withdrawal successful. reference = {}", transaction.getTxReference());

        return transactionMapper.mapToResponse(transaction);
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest request, String idempotencyKey) {
        idempotencyKeyService.validate(idempotencyKey);
        validateSelfTransfer(request);

        Wallet senderWallet = walletService.findById(request.senderWalletId());
        Wallet receiverWallet = walletService.findById(request.receiverWalletId());

        validateWallet(senderWallet);
        validateWallet(receiverWallet);

        BigDecimal senderBalanceBefore = senderWallet.getBalance();
        BigDecimal senderBalanceAfter = senderBalanceBefore.subtract(request.amount());

        BigDecimal receiverBalanceBefore = receiverWallet.getBalance();
        BigDecimal receiverBalanceAfter = receiverBalanceBefore.add(request.amount());

        senderWallet.debit(request.amount());
        receiverWallet.credit(request.amount());

        Transaction transaction = createTransaction(
                senderWallet,
                TransactionType.TRANSFER,
                request.amount(),
                "Wallet-Transfer"
        );

        ledgerEntryService.createEntry(
                senderWallet,
                transaction,
                request.amount(),
                senderBalanceBefore,
                senderBalanceAfter,
                LedgerEntryType.DEBIT,
                "Wallet Transfer"
        );
        ledgerEntryService.createEntry(
                receiverWallet,
                transaction,
                request.amount(),
                receiverBalanceBefore,
                receiverBalanceAfter,
                LedgerEntryType.CREDIT,
                "Wallet Transfer"
        );
        markSuccessful(transaction);
        idempotencyKeyService.save(idempotencyKey);
        log.info("Transfer successful. reference: {}", transaction.getTxReference());


        return transactionMapper.mapToResponse(transaction);
    }
@Transactional
    public Page<TransactionResponse> getTransactions(Long walletId, int page, int size) {
        walletService.findById(walletId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Transaction> transactions = transactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId, pageable);

        return transactions.map(transactionMapper::mapToResponse);
    }

    private void validateWallet(Wallet wallet) {
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new InactiveWalletException ("Wallet is not active");
        }
    }

    private Transaction createTransaction(Wallet wallet, TransactionType type, BigDecimal amount, String narration) {
        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .txReference(referenceGenerator())
                .transactionType(type)
                .status(TransactionStatus.PENDING)
                .amount(amount)
                .narration(narration)
                .createdAt(Instant.now())
                .build();
        return transactionRepository.save(transaction);
    }

    private String referenceGenerator() {
        return "TXN-" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();

    }

    private void markSuccessful(Transaction transaction) {
        transaction.setStatus(TransactionStatus.SUCCESS);
        transactionRepository.save(transaction);

    }

    private void validateSelfTransfer(TransferRequest request){
        Wallet senderWallet = walletService.findById(request.senderWalletId());
        Wallet receiverWallet = walletService.findById(request.receiverWalletId());
        if (senderWallet.equals(receiverWallet)){
            throw new IllegalTransactionStateException("Self-transfer is prohibited");
        }

    }

}
