package com.bashorundino.nairawallet.service.transaction;

import com.bashorundino.nairawallet.dto.request.WithdrawRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.IdempotencyKey;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.enums.IdempotencyStatus;
import com.bashorundino.nairawallet.enums.TransactionType;
import com.bashorundino.nairawallet.exception.DuplicateTransactionException;
import com.bashorundino.nairawallet.exception.IdempotencyClaimConflictException;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.UserRepository;
import com.bashorundino.nairawallet.service.CurrentUserService;
import com.bashorundino.nairawallet.service.IdempotencyKeyService;
import com.bashorundino.nairawallet.service.RequestFingerprintService;
import com.bashorundino.nairawallet.service.WithdrawalTransactionProcessor;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
@RequiredArgsConstructor
public class WithdrawalServiceImp implements WithdrawalService {

    private final WithdrawalTransactionProcessor withdrawalTransactionProcessor;
    private final IdempotencyKeyService idempotencyKeyService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final RequestFingerprintService requestFingerprintService;
    private final TransactionMapper transactionMapper;

    @Override
    public TransactionResponse withdrawal(
            WithdrawRequest request,
            String idempotencyKey) {

        String email = currentUserService.getCurrentUserEmail();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new EntityNotFoundException("Authenticated user not found"));

        String requestHash = requestFingerprintService.forWithdrawal(
                request.walletId(),
                request.amount()
        );

        try {
            return withdrawalTransactionProcessor.process(
                    request,
                    idempotencyKey,
                    user.getId(),
                    requestHash
            );
        } catch (IdempotencyClaimConflictException exception) {

            IdempotencyKey existing = idempotencyKeyService.findExisting(
                    user.getId(),
                    TransactionType.WITHDRAWAL,
                    idempotencyKey
            );

            if (existing == null) {
                throw exception;
            }

            idempotencyKeyService.validateSameRequest(existing, requestHash);

            if (existing.getStatus() == IdempotencyStatus.COMPLETED
                    && existing.getTransaction() != null) {

                return transactionMapper.mapToResponse(
                        existing.getTransaction(),
                        request.walletId()
                );
            }

            throw new DuplicateTransactionException(
                    "This request is already being processed"
            );
        }
    }
}