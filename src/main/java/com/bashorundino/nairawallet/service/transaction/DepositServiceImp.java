package com.bashorundino.nairawallet.service.transaction;

import com.bashorundino.nairawallet.dto.request.DepositRequest;
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
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DepositServiceImp implements DepositService {

    private final IdempotencyKeyService idempotencyKeyService;
    private final DepositTransactionProcessor depositTransactionProcessor;

    private final TransactionMapper transactionMapper;
    private final RequestFingerprintService requestFingerprintService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;


    @Override
    public TransactionResponse deposit(DepositRequest request, String idempotencyKey) {

        log.info("Deposit request received. Wallet = {}, Amount = {}",
                request.walletId(), request.amount()
        );

        String email = currentUserService.getCurrentUserEmail();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Authenticated user not found")
                );
        String requestHash = requestFingerprintService.forDeposit(
                request.walletId(),
                request.amount()
        );

        try {
            return depositTransactionProcessor.process(
                    request,
                    idempotencyKey,
                    user.getId(),
                    requestHash
            );
        } catch (IdempotencyClaimConflictException exception) {

            IdempotencyKey existing = idempotencyKeyService.findExisting(
                    user.getId(),
                    TransactionType.DEPOSIT,
                    idempotencyKey
            );
            if (existing == null) {
                // The competing transaction may have rolled back.
                // Do not assume this is a completed duplicate.
                throw exception;
            }
            idempotencyKeyService.validateSameRequest(existing, requestHash);

            if (existing.getStatus() == IdempotencyStatus.COMPLETED
                    && existing.getTransaction() != null
            ) {

                return transactionMapper.mapToResponse(
                        existing.getTransaction(),
                        request.walletId());
            }
            throw new DuplicateTransactionException(
                    "This request is already being processed"
            );
        }
    }
}