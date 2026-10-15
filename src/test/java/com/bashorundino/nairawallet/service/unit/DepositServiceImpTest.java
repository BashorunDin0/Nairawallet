package com.bashorundino.nairawallet.service.unit;


import com.bashorundino.nairawallet.dto.request.DepositRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.IdempotencyKey;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.TransactionType;
import com.bashorundino.nairawallet.exception.IdempotencyClaimConflictException;
import com.bashorundino.nairawallet.exception.InactiveWalletException;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.UserRepository;
import com.bashorundino.nairawallet.service.*;
import com.bashorundino.nairawallet.service.transaction.DepositServiceImp;
import com.bashorundino.nairawallet.service.transaction.DepositTransactionProcessor;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class DepositServiceImpTest {


    @Mock
    private IdempotencyKeyService idempotencyKeyService;

    @Mock
    private DepositTransactionProcessor depositTransactionProcessor;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private RequestFingerprintService requestFingerprintService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DepositServiceImp depositService;

    @Test
    @DisplayName("Deposit Money Successfully")
    void shouldDepositSuccessfully() {
        // Arrange
        Long userId = 1L;
        Long walletId = 10L;
        String email = "ibrahim@gmail.com";
        String idempotencyKey = "Deposit-001";
        String requestHash = "a".repeat(64);

        User user = createUser(
                "Ibrahim Olawale",
                "ibrahim",
                "070"
        );
        ReflectionTestUtils.setField(user, "id", userId);

        DepositRequest request = new DepositRequest(
                walletId,
                new BigDecimal("500.00")
        );

        TransactionResponse expectedResponse =
                mock(TransactionResponse.class);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(requestFingerprintService.forDeposit(
                walletId,
                new BigDecimal("500.00")
        )).thenReturn(requestHash);

        when(depositTransactionProcessor.process(
                request,
                idempotencyKey,
                userId,
                requestHash
        )).thenReturn(expectedResponse);

        // Act
        TransactionResponse response =
                depositService.deposit(request, idempotencyKey);

        // Assert
        assertEquals(expectedResponse, response);

        verify(currentUserService).getCurrentUserEmail();
        verify(userRepository).findByEmail(email);

        verify(requestFingerprintService).forDeposit(
                walletId,
                new BigDecimal("500.00")
        );

        verify(depositTransactionProcessor).process(
                request,
                idempotencyKey,
                userId,
                requestHash
        );

        verifyNoInteractions(idempotencyKeyService, transactionMapper);
    }

    @Test
    @DisplayName("Should reject deposit when authenticated user does not exist")
    void shouldRejectDepositWhenAuthenticatedUserDoesNotExist() {
        // Arrange
        String email = "missing@gmail.com";

        DepositRequest request = new DepositRequest(
                1L,
                new BigDecimal("500.00")
        );

        when(currentUserService.getCurrentUserEmail())
                .thenReturn(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                EntityNotFoundException.class,
                () -> depositService.deposit(request, "deposit-auth-test")
        );

        verify(userRepository).findByEmail(email);
        verifyNoInteractions(
                requestFingerprintService,
                depositTransactionProcessor
        );
    }



    @Test
    @DisplayName("Should propagate inactive wallet failure from processor")
    void shouldPropagateInactiveWalletFailure() {
        // Arrange
        Long userId = 1L;
        String email = "ibrahim@gmail.com";
        String idempotencyKey = "Deposit-002";
        String requestHash = "b".repeat(64);

        User user = createUser(
                "Ibrahim Olawale",
                "ibrahim",
                "080"
        );
        ReflectionTestUtils.setField(user, "id", userId);

        DepositRequest request = new DepositRequest(
                1L,
                new BigDecimal("200.00")
        );

        when(currentUserService.getCurrentUserEmail())
                .thenReturn(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(requestFingerprintService.forDeposit(
                request.walletId(),
                request.amount()
        )).thenReturn(requestHash);

        when(depositTransactionProcessor.process(
                request,
                idempotencyKey,
                userId,
                requestHash
        )).thenThrow(new InactiveWalletException("Wallet is inactive"));

        // Act & Assert
        assertThrows(
                InactiveWalletException.class,
                () -> depositService.deposit(request, idempotencyKey)
        );

        verify(depositTransactionProcessor).process(
                request,
                idempotencyKey,
                userId,
                requestHash
        );

        verifyNoInteractions(idempotencyKeyService, transactionMapper);
    }




    @Test
    @DisplayName("Should propagate idempotency conflict when no existing record is found")
    void shouldPropagateIdempotencyConflictWhenNoExistingRecordIsFound() {
        // Arrange
        Long userId = 1L;
        String email = "idempotency@gmail.com";
        String idempotencyKey = "IDEMP-001";
        String requestHash = "c".repeat(64);

        User user = createUser(
                "Idempotency User",
                "idempotency",
                "080"
        );
        ReflectionTestUtils.setField(user, "id", userId);

        DepositRequest request = new DepositRequest(
                10L,
                new BigDecimal("500.00")
        );

        IdempotencyClaimConflictException conflict =
                new IdempotencyClaimConflictException(
                        "Idempotency key has already been claimed",
                        new RuntimeException("Simulated claim conflict")
                );

        when(currentUserService.getCurrentUserEmail())
                .thenReturn(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(requestFingerprintService.forDeposit(
                request.walletId(),
                request.amount()
        )).thenReturn(requestHash);

        when(depositTransactionProcessor.process(
                request,
                idempotencyKey,
                userId,
                requestHash
        )).thenThrow(conflict);

        when(idempotencyKeyService.findExisting(
                userId,
                TransactionType.DEPOSIT,
                idempotencyKey
        )).thenReturn(null);

        // Act & Assert
        IdempotencyClaimConflictException thrown = assertThrows(
                IdempotencyClaimConflictException.class,
                () -> depositService.deposit(request, idempotencyKey)
        );

        assertEquals(conflict, thrown);

        verify(idempotencyKeyService).findExisting(
                userId,
                TransactionType.DEPOSIT,
                idempotencyKey
        );

        verify(idempotencyKeyService, never())
                .validateSameRequest(any(), anyString());

        verifyNoInteractions(transactionMapper);
    }


    @Test
    @DisplayName("Should return original response for completed duplicate deposit")
    void shouldReturnOriginalResponseForCompletedDuplicateDeposit() {
        // Arrange
        Long userId = 1L;
        Long walletId = 10L;
        String email = "idempotency-success@gmail.com";
        String idempotencyKey = "IDEMP-SUCCESS-001";
        String requestHash = "d".repeat(64);

        User user = createUser(
                "Idempotency Success User",
                "idempotency-success",
                "080"
        );
        ReflectionTestUtils.setField(user, "id", userId);

        Wallet wallet = Wallet.createFor(user);
        ReflectionTestUtils.setField(wallet, "id", walletId);

        DepositRequest request = new DepositRequest(
                walletId,
                new BigDecimal("500.00")
        );

        Transaction originalTransaction = Transaction.createDeposit(
                "TXN-IDEMP-001",
                new BigDecimal("500.00"),
                "Wallet Deposit",
                wallet
        );

        IdempotencyKey existingKey = IdempotencyKey.create(
                idempotencyKey,
                user,
                TransactionType.DEPOSIT,
                requestHash
        );
        existingKey.markCompleted(originalTransaction);

        TransactionResponse expectedResponse = mock(TransactionResponse.class);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(requestFingerprintService.forDeposit(
                walletId,
                request.amount()
        )).thenReturn(requestHash);

        when(depositTransactionProcessor.process(
                request,
                idempotencyKey,
                userId,
                requestHash
        )).thenThrow(new IdempotencyClaimConflictException(
                "Idempotency key has already been claimed",
                new RuntimeException("Simulated concurrent request")
        ));

        when(idempotencyKeyService.findExisting(
                userId,
                TransactionType.DEPOSIT,
                idempotencyKey
        )).thenReturn(existingKey);

        when(transactionMapper.mapToResponse(originalTransaction, walletId))
                .thenReturn(expectedResponse);

        // Act
        TransactionResponse response =
                depositService.deposit(request, idempotencyKey);

        // Assert
        assertEquals(expectedResponse, response);

        verify(idempotencyKeyService).validateSameRequest(
                existingKey,
                requestHash
        );

        verify(transactionMapper).mapToResponse(
                originalTransaction,
                walletId
        );

        verify(depositTransactionProcessor).process(
                request,
                idempotencyKey,
                userId,
                requestHash
        );
    }


    private User createUser(
            String fullName,
            String emailPrefix,
            String phonePrefix
    ) {
        return User.create(
                fullName,
                emailPrefix + "@gmail.com",
                "password123",
                phonePrefix + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
        );
    }
}

