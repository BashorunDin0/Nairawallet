package com.bashorundino.nairawallet.service;


import com.bashorundino.nairawallet.dto.request.DepositRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.IdempotencyKey;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.LedgerEntryType;
import com.bashorundino.nairawallet.enums.TransactionType;
import com.bashorundino.nairawallet.exception.IdempotencyClaimConflictException;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import com.bashorundino.nairawallet.repository.UserRepository;
import com.bashorundino.nairawallet.service.transaction.TransactionLedgerValidator;
import com.bashorundino.nairawallet.service.transaction.TransactionReferenceGenerator;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class DepositTransactionProcessor {

        private final UserRepository userRepository;
        private final WalletService walletService;
        private final LedgerEntryService ledgerEntryService;
        private final IdempotencyKeyService idempotencyKeyService;
        private final TransactionRepository transactionRepository;
        private final TransactionMapper transactionMapper;
        private final TransactionReferenceGenerator referenceGenerator;
        private final TransactionLedgerValidator transactionLedgerValidator;

        @Transactional
        public TransactionResponse process(
                DepositRequest request,
                String idempotencyKey,
                Long userId,
                String requestHash) {

            User user = userRepository.findById(userId)
                    .orElseThrow(() ->
                            new EntityNotFoundException("Authenticated user not found"));

            IdempotencyKey claimedKey;

            try {
                claimedKey = idempotencyKeyService.create(
                        idempotencyKey,
                        user,
                        TransactionType.DEPOSIT,
                        requestHash
                );
            } catch (DataIntegrityViolationException exception) {
                // This method's key insert is the operation that failed.
                // Throwing lets Spring roll back this entire transaction.
                throw new IdempotencyClaimConflictException(
                        "Idempotency key has already been claimed",
                        exception
                );
            }

            Wallet wallet =
                    walletService.findByIdForCurrentUser(request.walletId());

            BigDecimal balanceBefore = wallet.getBalance();

            wallet.credit(request.amount());

            BigDecimal balanceAfter = wallet.getBalance();

            Transaction transaction = Transaction.createDeposit(
                    referenceGenerator.generate(),
                    request.amount(),
                    "Wallet Deposit",
                    wallet
            );

            transactionRepository.save(transaction);

            ledgerEntryService.createEntry(
                    wallet,
                    transaction,
                    request.amount(),
                    balanceBefore,
                    balanceAfter,
                    LedgerEntryType.CREDIT,
                    "Wallet Deposit"
            );

            transactionLedgerValidator.validate(transaction);

            transaction.markSuccessful();

            idempotencyKeyService.markCompleted(claimedKey, transaction);

            return transactionMapper.mapToResponse(
                    transaction,
                    wallet.getId()
            );
        }
    }
