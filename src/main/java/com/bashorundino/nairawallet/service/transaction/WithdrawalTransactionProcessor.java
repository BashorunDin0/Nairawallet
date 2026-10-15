package com.bashorundino.nairawallet.service.transaction;

import com.bashorundino.nairawallet.dto.request.WithdrawRequest;
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
import com.bashorundino.nairawallet.service.IdempotencyKeyService;
import com.bashorundino.nairawallet.service.LedgerEntryService;
import com.bashorundino.nairawallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalTransactionProcessor {

        private final WalletService walletService;
        private final LedgerEntryService ledgerEntryService;
        private final IdempotencyKeyService idempotencyKeyService;
        private final TransactionRepository transactionRepository;
        private final TransactionMapper transactionMapper;
        private final TransactionReferenceGenerator referenceGenerator;
        private final TransactionLedgerValidator transactionLedgerValidator;
        private final UserRepository userRepository;

        @Transactional
        public TransactionResponse process(
                WithdrawRequest request,
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
                        TransactionType.WITHDRAWAL,
                        requestHash
                );
            } catch (DataIntegrityViolationException exception) {
                throw new IdempotencyClaimConflictException(
                        "Idempotency key has already been claimed",
                        exception
                );
            }

            Wallet wallet =
                    walletService.findByIdForCurrentUser(request.walletId());

            BigDecimal balanceBefore = wallet.getBalance();

            wallet.debit(request.amount());

            BigDecimal balanceAfter = wallet.getBalance();

            Transaction transaction = Transaction.createWithdrawal(
                    referenceGenerator.generate(),
                    request.amount(),
                    "Wallet Withdrawal",
                    wallet
            );

            transactionRepository.save(transaction);

            ledgerEntryService.createEntry(
                    wallet,
                    transaction,
                    request.amount(),
                    balanceBefore,
                    balanceAfter,
                    LedgerEntryType.DEBIT,
                    "Wallet Withdrawal"
            );

            transactionLedgerValidator.validate(transaction);
            transaction.markSuccessful();

            idempotencyKeyService.markCompleted(claimedKey, transaction);

            log.info(
                    "Withdrawal successful. reference = {}",
                    transaction.getTxReference()
            );

            return transactionMapper.mapToResponse(
                    transaction,
                    wallet.getId()
            );
        }
    }
