package com.bashorundino.nairawallet.service.unit;

import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.exception.WalletNotFoundException;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import com.bashorundino.nairawallet.service.CurrentUserService;
import com.bashorundino.nairawallet.service.transaction.TransactionServiceImp;
import com.bashorundino.nairawallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Transaction Service Implementation Test")
public class TransactionServiceImpTest {
    @Mock
    private WalletService walletService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private TransactionServiceImp transactionService;

    @Test
    @DisplayName("Should retrieve transactions for wallet")
    void shouldGetTransactionsForWallet() {

        // Arrange
        Long walletId = 2L;

        User sender = createUser(
                "Sender User" + UUID.randomUUID(),
                "history-sender",
                "0801"
        );

        User receiver = createUser(
                "Receiver User" + UUID.randomUUID(),
                "history-receiver",
                "0801"
        );

        Wallet senderWallet = Wallet.createFor(sender);
        Wallet receiverWallet = Wallet.createFor(receiver);

        ReflectionTestUtils.setField(
                senderWallet, "id", 1L);

        ReflectionTestUtils.setField(
                receiverWallet, "id", walletId);

        Transaction transaction = Transaction.createTransfer(
                "TXN-HISTORY-001",
                new BigDecimal("300.00"),
                "Wallet Transfer",
                senderWallet,
                receiverWallet
        );

        Page<Transaction> transactionPage =
                new PageImpl<>(List.of(transaction));

        TransactionResponse expectedResponse = mock(TransactionResponse.class);

        when(walletService.findById(walletId))
                .thenReturn(receiverWallet);
        when(currentUserService.getCurrentUserEmail())
                .thenReturn("history-receiver@gmail.com");

        when(transactionRepository
                .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        eq(walletId),
                        eq(walletId),
                        any(Pageable.class)
                ))
                .thenReturn(transactionPage);

        when(transactionMapper.mapToResponse(
                transaction,
                walletId
        )).thenReturn(expectedResponse);

        // Act
        org.springframework.data.domain.Page<TransactionResponse> response =
                transactionService.getTransactions(
                        walletId,
                        0,
                        10
                );

        // Assert
        assertEquals(1, response.getTotalElements());
        assertEquals(expectedResponse, response.getContent().get(0));

        verify(walletService).findById(walletId);

        verify(transactionRepository)
                .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        eq(walletId),
                        eq(walletId),
                        any(Pageable.class)
                );

        verify(transactionMapper)
                .mapToResponse(
                        transaction,
                        walletId
                );
    }

    @Test
    @DisplayName("Should return empty transaction history when wallet has no transactions")
    void shouldReturnEmptyTransactionHistory() {

        // Arrange
        Long walletId = 2L;

        Wallet wallet = mock(Wallet.class);
        User user = createUser(
                "History User",
                "history-user",
                "080"
        );

        when(wallet.getUser())
                .thenReturn(user);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn("history-user@gmail.com");

        Page<Transaction> emptyPage = Page.empty();

        when(walletService.findById(walletId))
                .thenReturn(wallet);

        when(transactionRepository
                .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        eq(walletId),
                        eq(walletId),
                        any(Pageable.class)
                ))
                .thenReturn(emptyPage);

        // Act
        Page<TransactionResponse> response =
                transactionService.getTransactions(
                        walletId,
                        0,
                        10
                );

        // Assert
        assertNotNull(response);
        assertTrue(response.isEmpty());
        assertEquals(0, response.getTotalElements());

        verify(walletService).findById(walletId);

        verify(transactionRepository)
                .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        eq(walletId),
                        eq(walletId),
                        any(Pageable.class)
                );

        verifyNoInteractions(transactionMapper);
    }

    @Test
    @DisplayName("Should reject transaction history request for non-existent wallet")
    void shouldRejectTransactionHistoryForNonExistentWallet() {

        // Arrange
        Long walletId = 912L;

        when(walletService.findById(walletId))
                .thenThrow(new WalletNotFoundException(
                        "Wallet not found: " + walletId
                ));

        // Act & Assert
        assertThrows(
                WalletNotFoundException.class,
                () -> transactionService.getTransactions(
                        walletId,
                        0,
                        10
                )
        );

        verify(walletService).findById(walletId);

        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(transactionMapper);
    }

    @Test
    @DisplayName("Should apply pagination when retrieving transaction history")
    void shouldApplyPaginationWhenRetrievingTransactions() {

        // Arrange
        Long walletId = 2L;

        Wallet wallet = mock(Wallet.class);

        User user = createUser(
                "pager nation",
                "pagination-user",
                "080"
        );

        when(wallet.getUser())
                .thenReturn(user);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn("pagination-user@gmail.com");

        Page<Transaction> transactionPage =
                new PageImpl<>(List.of());

        when(walletService.findById(walletId))
                .thenReturn(wallet);

        when(transactionRepository
                .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        eq(walletId),
                        eq(walletId),
                        any(Pageable.class)
                ))
                .thenReturn(transactionPage);

        // Act
        transactionService.getTransactions(
                walletId,
                1,
                5
        );

        // Assert
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(transactionRepository)
                .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        eq(walletId),
                        eq(walletId),
                        pageableCaptor.capture()
                );

        Pageable pageable = pageableCaptor.getValue();

        assertEquals(1, pageable.getPageNumber());
        assertEquals(5, pageable.getPageSize());
    }

    @Test
    @DisplayName("Should reject transaction history access for another user's wallet")
    void shouldRejectAccessToAnotherUsersWallet() {

        // Arrange
        Long walletId = 2L;

        User walletOwner = createUser(
                "Wallet Owner" + UUID.randomUUID(),
                "owner@gmail.com",
                "080"
        );

        Wallet wallet = Wallet.createFor(walletOwner);

        ReflectionTestUtils.setField(wallet, "id", walletId);

        when(walletService.findById(walletId))
                .thenReturn(wallet);

        when(currentUserService.getCurrentUserEmail())
                .thenReturn("attacker@gmail.com");

        // Act & Assert
        assertThrows(
                AccessDeniedException.class,
                () -> transactionService.getTransactions(
                        walletId,
                        0,
                        10
                )
        );

        // Verify repository was never reached
        verify(transactionRepository, never())
                .findBySourceWalletIdOrDestinationWalletIdOrderByCreatedAtDescIdDesc(
                        anyLong(),
                        anyLong(),
                        any(Pageable.class)
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
