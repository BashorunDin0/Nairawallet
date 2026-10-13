package com.bashorundino.nairawallet.service.unit;


import com.bashorundino.nairawallet.dto.request.TransferRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.exception.InactiveWalletException;
import com.bashorundino.nairawallet.exception.InsufficientFundsException;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import com.bashorundino.nairawallet.repository.UserRepository;
import com.bashorundino.nairawallet.service.*;
import com.bashorundino.nairawallet.service.transaction.TransactionReferenceGenerator;
import com.bashorundino.nairawallet.service.transaction.TransferServiceImp;
import com.bashorundino.nairawallet.service.transaction.TransferTransactionProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransferServiceImpTest {

    @Mock
    private WalletService walletService;

    @Mock
    private IdempotencyKeyService idempotencyKeyService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private RequestFingerprintService requestFingerprintService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LedgerEntryService ledgerEntryService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private TransactionReferenceGenerator referenceGenerator;


    @Mock
    private TransferTransactionProcessor transferTransactionProcessor;


    @InjectMocks
    private TransferServiceImp transferService;


    @Test
    @DisplayName("Transfer Money Successfully")
    void shouldTransferSuccessfully() {
        // Arrange
        Long userId = 1L;
        String email = "ibrahim@gmail.com";
        String requestHash = "a".repeat(64);
        String idempotencyKey = "Transfer-001";

        TransferRequest request = new TransferRequest(
                1L,
                2L,
                new BigDecimal("300.00"),
                "Transfer money"
        );

        User sender = createUser(
                "Ibrahim Olawale",
                "ibrahim",
                "0801"
        );
        ReflectionTestUtils.setField(sender, "id", userId);

        TransactionResponse expectedResponse = mock(TransactionResponse.class);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(sender));

        when(requestFingerprintService.forTransfer(
                request.senderWalletId(),
                request.receiverWalletId(),
                request.amount()
        )).thenReturn(requestHash);

        when(transferTransactionProcessor.process(
                request,
                idempotencyKey,
                userId,
                requestHash
        )).thenReturn(expectedResponse);

        // Act
        TransactionResponse response =
                transferService.transfer(request, idempotencyKey);

        // Assert
        assertEquals(expectedResponse, response);

        verify(currentUserService).getCurrentUserEmail();
        verify(userRepository).findByEmail(email);

        verify(requestFingerprintService).forTransfer(
                request.senderWalletId(),
                request.receiverWalletId(),
                request.amount()
        );

        verify(transferTransactionProcessor).process(
                request,
                idempotencyKey,
                userId,
                requestHash
        );

        verifyNoInteractions(idempotencyKeyService, transactionMapper);
    }

    @Test
    @DisplayName("Should reject self transfer")
    void shouldRejectSelfTransfer() {
        // Arrange
        Long walletId = 1L;
        String idempotencyKey = "Transfer-002";
        String email = "ibrahim@gmail.com";
        Long userId = 10L;
        String requestHash = "a".repeat(64);

        var request = new TransferRequest(
                walletId,
                walletId,
                new BigDecimal("300.00"),
                "Transfer"
        );

        User user = createUser(
                "Ibrahim Olawale" + UUID.randomUUID(),
                email,
                "0801"
        );

        ReflectionTestUtils.setField(user, "id", userId);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));

        when(requestFingerprintService.forTransfer(
                walletId,
                walletId,
                request.amount()
        )).thenReturn(requestHash);

        when(transferTransactionProcessor.process(
                request,
                idempotencyKey,
                userId,
                requestHash
        )).thenThrow(new IllegalTransactionStateException(
                "Sender and receiver wallets must be different"
        ));

        // Act & Assert
        assertThrows(
                IllegalTransactionStateException.class,
                () -> transferService.transfer(request, idempotencyKey)
        );

        // Verify the processor was called with the correct request
        verify(transferTransactionProcessor).process(
                request,
                idempotencyKey,
                userId,
                requestHash
        );

        // Verify the outer service did not attempt idempotency replay
        verifyNoInteractions(transactionRepository, ledgerEntryService);
    }

    @Test
    @DisplayName("Should reject transfer from inactive sender wallet")
    void shouldRejectTransferFromInactiveSenderWallet() {
        // Arrange
        Long senderWalletId = 1L;
        Long receiverWalletId = 2L;
        Long userId = 10L;

        String email = "ib@gmail.com";
        String idempotencyKey = "Transfer-004";
        String requestHash = "a".repeat(64);

        var request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("300.00"),
                "Transfer"
        );

        User sender = createUser(
                "Ibrahim Olawale" + UUID.randomUUID(),
                email,
                "08012345686"
        );

        ReflectionTestUtils.setField(sender, "id", userId);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(sender));

        when(requestFingerprintService.forTransfer(
                senderWalletId,
                receiverWalletId,
                request.amount()
        )).thenReturn(requestHash);

        when(transferTransactionProcessor.process(
                request,
                idempotencyKey,
                userId,
                requestHash
        )).thenThrow(new InactiveWalletException(
                "Wallet is inactive"
        ));

        // Act & Assert
        assertThrows(
                InactiveWalletException.class,
                () -> transferService.transfer(request, idempotencyKey)
        );

        // Verify the processor received the request
        verify(transferTransactionProcessor).process(
                request,
                idempotencyKey,
                userId,
                requestHash
        );

        // The mocked processor doesn't modify wallets or create ledger entries.
        verifyNoInteractions(transactionRepository, ledgerEntryService);
    }

    @Test
    @DisplayName("Should reject transfer to inactive receiver wallet")
    void shouldRejectTransferToInactiveReceiverWallet() {

        // Arrange
        Long senderWalletId = 1L;
        Long receiverWalletId = 2L;
        Long userId = 10L;

        String email = "ib@gmail.com";
        String idempotencyKey = "Transfer-004";
        String requestHash = "a".repeat(64);

        var request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("300.00"),
                "Transfer"
        );

        User receiver = createUser(
                "Ibrahim Olawale" + UUID.randomUUID(),
                email,
                "08012345686"
        );

        ReflectionTestUtils.setField(receiver, "id", userId);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(receiver));

        when(requestFingerprintService.forTransfer(
                senderWalletId,
                receiverWalletId,
                request.amount()
        )).thenReturn(requestHash);

        when(transferTransactionProcessor.process(
                request,
                idempotencyKey,
                userId,
                requestHash
        )).thenThrow(new InactiveWalletException(
                "Wallet is inactive"
        ));

        // Act & Assert
        assertThrows(
                InactiveWalletException.class,
                () -> transferService.transfer(request, idempotencyKey)
        );

        // Verify the processor received the request
        verify(transferTransactionProcessor).process(
                request,
                idempotencyKey,
                userId,
                requestHash
        );

        // The mocked processor doesn't modify wallets or create ledger entries.
        verifyNoInteractions(transactionRepository, ledgerEntryService);
    }

    @Test
    @DisplayName("Should reject transfer when sender wallet does not belong to current user")
    void shouldRejectTransferFromWalletOwnedByAnotherUser() {
        // Arrange
        Long senderWalletId = 1L;
        Long receiverWalletId = 2L;
        Long userId = 10L;

        String email = "ib@gmail.com";
        String idempotencyKey = "transfer-auth-test";
        String requestHash = "a".repeat(64);

        TransferRequest request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("100.00"),
                "Unauthorized transfer"
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

        when(requestFingerprintService.forTransfer(
                senderWalletId,
                receiverWalletId,
                request.amount()
        )).thenReturn(requestHash);

        when(transferTransactionProcessor.process(
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
                () -> transferService.transfer(request, idempotencyKey)
        );

        // Verify delegation to the processor
        verify(transferTransactionProcessor).process(
                request,
                idempotencyKey,
                userId,
                requestHash
        );

        // The mocked processor does not execute wallet operations.
        verifyNoInteractions(transactionRepository, ledgerEntryService);
    }

    @Test
    @DisplayName("Should reject transfer when sender has insufficient funds")
    void shouldRejectTransferWhenSenderHasInsufficientFunds() {
        // Arrange
        Long senderWalletId = 1L;
        Long receiverWalletId = 2L;
        Long userId = 10L;

        String email = "sender-insufficient@gmail.com";
        String idempotencyKey = "Transfer-003";
        String requestHash = "a".repeat(64);

        var request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("1500.00"),
                "Transfer"
        );

        User senderUser = createUser(
                "Ibrahim Sender" + UUID.randomUUID(),
                email,
                "0801"
        );

        ReflectionTestUtils.setField(senderUser, "id", userId);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(senderUser));

        when(requestFingerprintService.forTransfer(
                senderWalletId,
                receiverWalletId,
                request.amount()
        )).thenReturn(requestHash);

        when(transferTransactionProcessor.process(
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
                () -> transferService.transfer(request, idempotencyKey)
        );

        // Verify the request reached the processor
        verify(transferTransactionProcessor).process(
                request,
                idempotencyKey,
                userId,
                requestHash
        );

        // The mocked processor cannot change balances or create entries.
        verifyNoInteractions(transactionRepository, ledgerEntryService);
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
