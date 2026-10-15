package com.bashorundino.nairawallet.service.unit;

import com.bashorundino.nairawallet.dto.request.WithdrawRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.exception.InsufficientFundsException;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import com.bashorundino.nairawallet.repository.UserRepository;
import com.bashorundino.nairawallet.service.*;
import com.bashorundino.nairawallet.service.transaction.WithdrawalServiceImp;
import com.bashorundino.nairawallet.service.transaction.WithdrawalTransactionProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WithdrawalServiceImpTest {

    @Mock
    private WalletService walletService;

    @Mock
    private LedgerEntryService ledgerEntryService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private WithdrawalTransactionProcessor withdrawalTransactionProcessor;

    @InjectMocks
    private WithdrawalServiceImp withdrawalService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private RequestFingerprintService requestFingerprintService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IdempotencyKeyService idempotencyKeyService;


    @Test
    @DisplayName("Withdraw money successfully")
    void shouldWithdrawSuccessfully() {
        // Arrange
        Long userId = 1L;
        String email = "idris@gmail.com";
        String requestHash = "b".repeat(64);
        String idempotencyKey = "Withdrawal-001";

        WithdrawRequest request = new WithdrawRequest(
                1L,
                new BigDecimal("300.00")
        );

        User user = createUser(
                "Idris Babatunde",
                "idris",
                "080"
        );
        ReflectionTestUtils.setField(user, "id", userId);

        TransactionResponse expectedResponse = mock(TransactionResponse.class);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(requestFingerprintService.forWithdrawal(
                request.walletId(),
                request.amount()
        )).thenReturn(requestHash);

        when(withdrawalTransactionProcessor.process(
                request,
                idempotencyKey,
                userId,
                requestHash
        )).thenReturn(expectedResponse);

        // Act
        TransactionResponse response =
                withdrawalService.withdrawal(request, idempotencyKey);

        // Assert
        assertEquals(expectedResponse, response);

        verify(currentUserService).getCurrentUserEmail();
        verify(userRepository).findByEmail(email);

        verify(requestFingerprintService).forWithdrawal(
                request.walletId(),
                request.amount()
        );

        verify(withdrawalTransactionProcessor).process(
                request,
                idempotencyKey,
                userId,
                requestHash
        );

        verifyNoInteractions(idempotencyKeyService, transactionMapper);
    }


    @Test
    @DisplayName("Should reject withdrawal when wallet does not belong to current user")
    void shouldRejectWithdrawalForWalletOwnedByAnotherUser() {
        // Arrange
        Long walletId = 1L;
        Long userId = 10L;

        String email = "ibrahim@gmail.com";
        String idempotencyKey = "withdraw-auth-test";
        String requestHash = "a".repeat(64);

        WithdrawRequest request = new WithdrawRequest(
                walletId,
                new BigDecimal("500.00")
        );

        User user = createUser(
                "Ibrahim Olawale" + UUID.randomUUID(),
                email,
                "08012345686"
        );

        ReflectionTestUtils.setField(user, "id", userId);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(requestFingerprintService.forWithdrawal(
                walletId,
                request.amount()
        )).thenReturn(requestHash);

        when(withdrawalTransactionProcessor.process(
                request,
                idempotencyKey,
                userId,
                requestHash
        )).thenThrow(new AccessDeniedException(
                "You are not allowed to access this wallet"
        ));

        // Act & Assert
        assertThrows(
                AccessDeniedException.class,
                () -> withdrawalService.withdrawal(request, idempotencyKey)
        );

        // Verify delegation to the processor
        verify(withdrawalTransactionProcessor).process(
                request,
                idempotencyKey,
                userId,
                requestHash
        );
    }

    @Test
    @DisplayName("Should reject withdrawal when funds are insufficient")
    void shouldRejectWithdrawalWhenFundsAreInsufficient() {
        // Arrange
        Long walletId = 1L;
        Long userId = 10L;

        String email = "ibrahim@gmail.com";
        String idempotencyKey = "Withdrawal-002";
        String requestHash = "a".repeat(64);

        var request = new WithdrawRequest(
                walletId,
                new BigDecimal("1500.00")
        );

        User user = createUser(
                "Ibrahim Olawale" + UUID.randomUUID(),
                email,
                "080"
        );

        ReflectionTestUtils.setField(user, "id", userId);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(requestFingerprintService.forWithdrawal(
                walletId,
                request.amount()
        )).thenReturn(requestHash);

        when(withdrawalTransactionProcessor.process(
                request,
                idempotencyKey,
                userId,
                requestHash
        )).thenThrow(new InsufficientFundsException(
                "Insufficient wallet balance"
        ));

        // Act & Assert
        assertThrows(
                InsufficientFundsException.class,
                () -> withdrawalService.withdrawal(request, idempotencyKey)
        );

        // Verify the request reached the processor
        verify(withdrawalTransactionProcessor).process(
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
