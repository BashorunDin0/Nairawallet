package com.bashorundino.nairawallet.service;

import com.bashorundino.nairawallet.dto.request.DepositRequest;
import com.bashorundino.nairawallet.dto.request.TransferRequest;
import com.bashorundino.nairawallet.dto.request.WithdrawRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.LedgerEntryType;
import com.bashorundino.nairawallet.enums.TransactionStatus;
import com.bashorundino.nairawallet.enums.TransactionType;
import com.bashorundino.nairawallet.enums.*;
import com.bashorundino.nairawallet.exception.InactiveWalletException;
import com.bashorundino.nairawallet.exception.InsufficientFundsException;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import com.bashorundino.nairawallet.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Transaction Service Implementation Test")
public class TransactionServiceImpTest {
    @Mock
    private WalletService walletService;

    @Mock
    private LedgerEntryService ledgerEntryService;

    @Mock
    private IdempotencyKeyService idempotencyKeyService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionServiceImp transactionService;

    @Test
    @DisplayName("Deposit Money Successfully")
    void shouldDepositSuccessfully() {
//        Arrange
        Long walletId = 1L;

        var request = new DepositRequest(
                walletId, new BigDecimal("500.00")
        );

        User user = User.builder()
                .fullName("Ibrahim Olawale")
                .email("ib@gmail.com")
                .phoneNumber("0703307345")
                .build();
        var wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

        var savedTransaction = Transaction.builder()
                .wallet(wallet)
                .txReference("TXN-ABC-001")
                .amount(new BigDecimal("500.00"))
                .transactionType(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .narration("Wallet Deposit")
                .build();

        var expectedResponse = mock(TransactionResponse.class);

        when(walletService.findById(walletId)).
                thenReturn(wallet);

        when(transactionRepository.save(any(Transaction.class))).
                thenReturn(savedTransaction);

        when(transactionMapper.mapToResponse(any(Transaction.class))).
                thenReturn(expectedResponse);

//        Act
        var response = transactionService.deposit(request, "Deposit-001");

        assertEquals(TransactionStatus.SUCCESS, savedTransaction.getStatus());

//        Assert
        assertEquals(new BigDecimal("1500.00"), wallet.getBalance());

        assertEquals(expectedResponse, response);

        verify(idempotencyKeyService).validate("Deposit-001");

        verify(walletService).findById(walletId);

        verify(transactionRepository, times(2)).save(any(Transaction.class));

        verify(ledgerEntryService).createEntry(
                eq(wallet),
                eq(savedTransaction),
                eq(new BigDecimal("500.00")),
                eq(new BigDecimal("1000.00")),
                eq(new BigDecimal("1500.00")),
                eq(LedgerEntryType.CREDIT),
                eq("Wallet Deposit")
        );

        verify(idempotencyKeyService).save("Deposit-001");

        verify(transactionMapper).mapToResponse(savedTransaction);

    }

    @Test
    @DisplayName("Should reject deposit into inactive wallet")
    void shouldRejectDepositIntoInactiveWallet() {
//      Arrange

        Long walletId = 1L;

        var request = new DepositRequest(
                walletId, new BigDecimal("200.00"));

        User user = User.builder()
                .fullName("Ibrahim Olawale")
                .email("Ib@gmail.com")
                .phoneNumber("08012345678")
                .build();

        Wallet wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

//        Make wallet inactive
        wallet.deactivate();

        when(walletService.findById(walletId)).
                thenReturn(wallet);

//        Act & Assert
        assertThrows(InactiveWalletException.class,
                () -> transactionService.deposit(request, "Deposit-002"));

//        Verify nothing was persisted
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(ledgerEntryService, never()).createEntry(
                any(Wallet.class),
                any(Transaction.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(LedgerEntryType.class),
                anyString()
        );
        verify(idempotencyKeyService, never()).save("Deposit-002");


    }

    @Test
    @DisplayName("Withdraw money successfully")
    public void shouldWithdrawSuccessfully() {
//        Arrange
        Long walletId = 1L;
        var request = new WithdrawRequest(walletId,
                new BigDecimal("300.00"));

        User user = User.builder()
                .fullName("Idris Babatunde")
                .email("idris@gmail.com")
                .phoneNumber("123456788")
                .build();

        var wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

        var savedTransaction = Transaction.builder()
                .wallet(wallet)
                .txReference("TXN-WTH-001")
                .amount(new BigDecimal("300.00"))
                .transactionType(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.PENDING)
                .narration("Wallet Withdrawal")
                .build();

        var expectedResponse = mock(TransactionResponse.class);

        when(walletService.findById(walletId)).
                thenReturn(wallet);

        when(transactionRepository.save(any(Transaction.class))).
                thenReturn(savedTransaction);

        when(transactionMapper.mapToResponse(any(Transaction.class)))
                .thenReturn(expectedResponse);

//        Act
        var response = transactionService.withdrawal(
                request, "Withdrawal-001");

//        Assert
        assertEquals(TransactionStatus.SUCCESS, savedTransaction.getStatus());

        assertEquals(new BigDecimal("700.00"), wallet.getBalance());

        assertEquals(expectedResponse, response);

        verify(idempotencyKeyService).validate("Withdrawal-001");

        verify(walletService).findById(walletId);

        verify(transactionRepository, times(2))
                .save(any(Transaction.class));

        verify(ledgerEntryService).createEntry(
                eq(wallet),
                eq(savedTransaction),
                eq(new BigDecimal("300.00")),
                eq(new BigDecimal("1000.00")),
                eq(new BigDecimal("700.00")),
                eq(LedgerEntryType.DEBIT),
                eq("Wallet Withdrawal")
        );

        verify(idempotencyKeyService).save("Withdrawal-001");

        verify(transactionMapper).mapToResponse(savedTransaction);
    }

    @Test
    @DisplayName("Should reject withdrawal when funds are insufficient")
    void shouldRejectWithdrawalWhenFundsAreInsufficient() {
//    Arrange
        Long walletId = 1L;

        var request = new WithdrawRequest(
                walletId,
                new BigDecimal("1500.00")
        );

        User user = User.builder()
                .fullName("Ibrahim Olawale")
                .email("ibrahim@gmail.com")
                .phoneNumber("123456777")
                .build();

        var wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

        when(walletService.findById(walletId))
                .thenReturn(wallet);

//    Act & Assert
        assertThrows(
                InsufficientFundsException.class,
                () -> transactionService.withdrawal(
                        request,
                        "Withdrawal-002"
                )
        );

//    Verify nothing was persisted
        verify(transactionRepository, never())
                .save(any(Transaction.class));

        verify(ledgerEntryService, never()).createEntry(
                any(Wallet.class),
                any(Transaction.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(LedgerEntryType.class),
                anyString()
        );

        verify(idempotencyKeyService, never())
                .save("Withdrawal-002");

//    Verify wallet balance was not changed
        assertEquals(
                new BigDecimal("1000.00"),
                wallet.getBalance()
        );
    }

    @Test
    @DisplayName("Transfer Money Successfully")
    void shouldTransferSuccessfully() {
//    Arrange
        Long senderWalletId = 1L;
        Long receiverWalletId = 2L;

        var request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("300.00"),
                "Transfer money"
        );

        User sender = User.builder()
                .fullName("Ibrahim Olawale")
                .email("ibrahim@gmail.com")
                .phoneNumber("08012345678")
                .build();

        var receiver = User.builder()
                .fullName("Idris Tunde")
                .email("idris@gmail.com")
                .phoneNumber("08012345676")
                .build();

        var senderWallet = Wallet.createFor(sender);
        senderWallet.credit(new BigDecimal("1000.00"));

        var receiverWallet = Wallet.createFor(receiver);
        receiverWallet.credit(new BigDecimal("500.00"));
//  Manually creating the database auto-generated Ids.
        ReflectionTestUtils.setField(senderWallet, "id", senderWalletId);
        ReflectionTestUtils.setField(receiverWallet, "id", receiverWalletId);

        var savedTransaction = Transaction.builder()
                .wallet(senderWallet)
                .txReference("TXN-TRANSFER-001")
                .amount(new BigDecimal("300.00"))
                .transactionType(TransactionType.TRANSFER)
                .status(TransactionStatus.PENDING)
                .narration("Wallet-Transfer")
                .build();

        var expectedResponse = mock(TransactionResponse.class);

        when(walletService.findById(senderWalletId))
                .thenReturn(senderWallet);

        when(walletService.findById(receiverWalletId))
                .thenReturn(receiverWallet);

        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(savedTransaction);

        when(transactionMapper.mapToResponse(any(Transaction.class)))
                .thenReturn(expectedResponse);

//    Act
        var response = transactionService.transfer(
                request,
                "Transfer-001"
        );

//    Assert
        assertEquals(TransactionStatus.SUCCESS, savedTransaction.getStatus());

        assertEquals(
                new BigDecimal("700.00"),
                senderWallet.getBalance()
        );

        assertEquals(
                new BigDecimal("800.00"),
                receiverWallet.getBalance()
        );

        assertEquals(expectedResponse, response);

//    Verify idempotency
        verify(idempotencyKeyService)
                .validate("Transfer-001");

        verify(idempotencyKeyService)
                .save("Transfer-001");

//    Verify both wallets were retrieved
        verify(walletService)
                .findById(senderWalletId);

        verify(walletService)
                .findById(receiverWalletId);

//    Transaction is saved twice:
//    1. Create transaction
//    2. Mark transaction successful
        verify(transactionRepository, times(2))
                .save(any(Transaction.class));

//    Verify sender ledger entry
        verify(ledgerEntryService).createEntry(
                eq(senderWallet),
                eq(savedTransaction),
                eq(new BigDecimal("300.00")),
                eq(new BigDecimal("1000.00")),
                eq(new BigDecimal("700.00")),
                eq(LedgerEntryType.DEBIT),
                eq("Wallet Transfer")
        );

//    Verify receiver ledger entry
        verify(ledgerEntryService).createEntry(
                eq(receiverWallet),
                eq(savedTransaction),
                eq(new BigDecimal("300.00")),
                eq(new BigDecimal("500.00")),
                eq(new BigDecimal("800.00")),
                eq(LedgerEntryType.CREDIT),
                eq("Wallet Transfer")
        );

//    Verify response mapping
        verify(transactionMapper)
                .mapToResponse(savedTransaction);
    }

    @Test
    @DisplayName("Should reject self transfer")
    void shouldRejectSelfTransfer() {
//    Arrange
        Long walletId = 1L;

        var request = new TransferRequest(
                walletId,
                walletId,
                new BigDecimal("300.00"),
                "Transfer"
        );

        User user = User.builder()
                .fullName("Ibrahim Olawale")
                .email("ibrahim@gmail.com")
                .phoneNumber("08012345683")
                .build();

        var wallet = Wallet.createFor(user);
        wallet.credit(new BigDecimal("1000.00"));

        ReflectionTestUtils.setField(
                wallet,
                "id",
                walletId
        );

        when(walletService.findById(walletId))
                .thenReturn(wallet);

//    Act & Assert
        assertThrows(
                IllegalTransactionStateException.class,
                () -> transactionService.transfer(
                        request,
                        "Transfer-002"
                )
        );

//    Verify wallet balance did not change
        assertEquals(
                new BigDecimal("1000.00"),
                wallet.getBalance()
        );

//    Verify no transaction was created
        verify(transactionRepository, never())
                .save(any(Transaction.class));

//    Verify no ledger entry was created
        verify(ledgerEntryService, never()).createEntry(
                any(Wallet.class),
                any(Transaction.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(LedgerEntryType.class),
                anyString()
        );

//    Verify idempotency key was not saved
        verify(idempotencyKeyService, never())
                .save("Transfer-002");
    }

    @Test
    @DisplayName("Should reject transfer when sender has insufficient funds")
    void shouldRejectTransferWhenSenderHasInsufficientFunds() {
//    Arrange
        Long senderWalletId = 1L;
        Long receiverWalletId = 2L;

        var request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("1500.00"),
                "Transfer"
        );

        User senderUser = User.builder()
                .fullName("Ibrahim Sender")
                .email("sender-insufficient@gmail.com")
                .phoneNumber("08012345684")
                .build();

        User receiverUser = User.builder()
                .fullName("Receiver User")
                .email("receiver-insufficient@gmail.com")
                .phoneNumber("08012345685")
                .build();

        var senderWallet = Wallet.createFor(senderUser);
        senderWallet.credit(new BigDecimal("1000.00"));

        var receiverWallet = Wallet.createFor(receiverUser);
        receiverWallet.credit(new BigDecimal("500.00"));

        ReflectionTestUtils.setField(
                senderWallet,
                "id",
                senderWalletId
        );

        ReflectionTestUtils.setField(
                receiverWallet,
                "id",
                receiverWalletId
        );

        when(walletService.findById(senderWalletId))
                .thenReturn(senderWallet);

        when(walletService.findById(receiverWalletId))
                .thenReturn(receiverWallet);

//    Act & Assert
        assertThrows(
                InsufficientFundsException.class,
                () -> transactionService.transfer(
                        request,
                        "Transfer-003"
                )
        );

//    Verify sender balance did not change
        assertEquals(
                new BigDecimal("1000.00"),
                senderWallet.getBalance()
        );

//    Verify receiver balance did not change
        assertEquals(
                new BigDecimal("500.00"),
                receiverWallet.getBalance()
        );

//    Verify no transaction was created
        verify(transactionRepository, never())
                .save(any(Transaction.class));

//    Verify no ledger entry was created
        verify(ledgerEntryService, never()).createEntry(
                any(Wallet.class),
                any(Transaction.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(LedgerEntryType.class),
                anyString()
        );

//    Verify idempotency key was not saved
        verify(idempotencyKeyService, never())
                .save("Transfer-003");
    }

    @Test
    @DisplayName("Should reject transfer from inactive sender wallet")
    void shouldRejectTransferFromInactiveSenderWallet() {
//    Arrange
        Long senderWalletId = 1L;
        Long receiverWalletId = 2L;

        var request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("300.00"),
                "Transfer"
        );

        User sender = User.builder()
                .fullName("Ibrahim Olawale")
                .email("ib@gmail.com")
                .phoneNumber("08012345686")
                .build();

        User receiver = User.builder()
                .fullName("Idris Tunde")
                .email("idris@gmail.com")
                .phoneNumber("08012345687")
                .build();

        var senderWallet = Wallet.createFor(sender);
        senderWallet.credit(new BigDecimal("1000.00"));
        senderWallet.deactivate();

        var receiverWallet = Wallet.createFor(receiver);
        receiverWallet.credit(new BigDecimal("500.00"));

        ReflectionTestUtils.setField(
                senderWallet,
                "id",
                senderWalletId
        );

        ReflectionTestUtils.setField(
                receiverWallet,
                "id",
                receiverWalletId
        );

        when(walletService.findById(senderWalletId))
                .thenReturn(senderWallet);

        when(walletService.findById(receiverWalletId))
                .thenReturn(receiverWallet);

//    Act & Assert
        assertThrows(
                InactiveWalletException.class,
                () -> transactionService.transfer(
                        request,
                        "Transfer-004"
                )
        );

//    Verify balances did not change
        assertEquals(
                new BigDecimal("1000.00"),
                senderWallet.getBalance()
        );

        assertEquals(
                new BigDecimal("500.00"),
                receiverWallet.getBalance()
        );

//    Verify no transaction was created
        verify(transactionRepository, never())
                .save(any(Transaction.class));

//    Verify no ledger entry was created
        verify(ledgerEntryService, never()).createEntry(
                any(Wallet.class),
                any(Transaction.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(LedgerEntryType.class),
                anyString()
        );

//    Verify idempotency key was not saved
        verify(idempotencyKeyService, never())
                .save("Transfer-004");
    }

    @Test
    @DisplayName("Should reject transfer to inactive receiver wallet")
    void shouldRejectTransferToInactiveReceiverWallet() {

        // Arrange
        Long senderWalletId = 1L;
        Long receiverWalletId = 2L;

        var request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("300.00"),
                "Transfer"
        );

        User sender = User.builder()
                .fullName("Active Sender")
                .email("active-sender-2@gmail.com")
                .phoneNumber("08012345688")
                .build();

        User receiver = User.builder()
                .fullName("Inactive Receiver")
                .email("inactive-receiver-2@gmail.com")
                .phoneNumber("08012345689")
                .build();

        var senderWallet = Wallet.createFor(sender);
        senderWallet.credit(new BigDecimal("1000.00"));

        var receiverWallet = Wallet.createFor(receiver);
        receiverWallet.credit(new BigDecimal("500.00"));
        receiverWallet.deactivate();

        ReflectionTestUtils.setField(senderWallet, "id", senderWalletId);
        ReflectionTestUtils.setField(receiverWallet, "id", receiverWalletId);

        when(walletService.findById(senderWalletId))
                .thenReturn(senderWallet);

        when(walletService.findById(receiverWalletId))
                .thenReturn(receiverWallet);

        // Act & Assert
        assertThrows(
                InactiveWalletException.class,
                () -> transactionService.transfer(
                        request,
                        "Transfer-005"
                )
        );

        // Verify no transaction was created
        verify(transactionRepository, never())
                .save(any(Transaction.class));

        // Verify no ledger entry was created
        verify(ledgerEntryService, never()).createEntry(
                any(Wallet.class),
                any(Transaction.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(LedgerEntryType.class),
                anyString()
        );

        // Verify idempotency key was not saved
        verify(idempotencyKeyService, never())
                .save("Transfer-005");
    }
}
