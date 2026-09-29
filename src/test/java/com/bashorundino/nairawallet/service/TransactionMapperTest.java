package com.bashorundino.nairawallet.service;


import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.TransactionDirection;
import com.bashorundino.nairawallet.enums.TransactionStatus;
import com.bashorundino.nairawallet.enums.TransactionType;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransactionMapperTest {

    private final TransactionMapper transactionMapper =
            new TransactionMapper();

    @Test
    void shouldMapTransferAsOutgoingForSender() {

//    Arrange

        Long senderWalletId = 1L;
        Long receiverWalletId = 2L;

        User sender = User.builder()
                .fullName("Mapper test")
                .email("sendermapper" + UUID.randomUUID() + "@gmail.com")
                .phoneNumber("080" + String.format(
                        "%08d", new Random().nextInt(100_000_000)))
                .build();

        User receiver = User.builder()
                .fullName("Mapper receiver")
                .email("receivermapper" + UUID.randomUUID() + "@gmail.com")
                .phoneNumber("080" + String.format(
                        "%08d", new Random().nextInt(100_000_000)))
                .build();

        Wallet senderWallet = Wallet.createFor(sender);
        Wallet receiverWallet = Wallet.createFor(receiver);

        ReflectionTestUtils.setField(senderWallet, "id", senderWalletId);
        ReflectionTestUtils.setField(receiverWallet, "id", receiverWalletId);

        Transaction transaction = Transaction.builder()
                .sourceWallet(senderWallet)
                .destinationWallet(receiverWallet)
                .txReference("TXN-TRANSFER_001")
                .amount(new BigDecimal("300.00"))
                .transactionType(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .narration("Wallet transfer")
                .build();
//  Act
        TransactionResponse response =
                transactionMapper.mapToResponse(
                        transaction,
                        senderWalletId
                );

//  Assert
        assertEquals(TransactionDirection.OUTGOING,
                response.direction());
    }

    @Test
    void shouldMapTransferAsIncomingForReceiver() {

//    Arrange

        Long senderWalletId = 1L;
        Long receiverWalletId = 2L;

        User sender = User.builder()
                .fullName("Mapper test2")
                .email("sendermapper2" + UUID.randomUUID() + "@gmail.com")
                .phoneNumber("080" + String.format(
                        "%08d", new Random().nextInt(100_000_000)))
                .build();

        User receiver = User.builder()
                .fullName("Mapper receiver2")
                .email("receivermapper2" + UUID.randomUUID() + "@gmail.com")
                .phoneNumber("080" + String.format(
                        "%08d", new Random().nextInt(100_000_000)))
                .build();

        Wallet senderWallet = Wallet.createFor(sender);
        Wallet receiverWallet = Wallet.createFor(receiver);

        ReflectionTestUtils.setField(senderWallet, "id", senderWalletId);
        ReflectionTestUtils.setField(receiverWallet, "id", receiverWalletId);

        Transaction transaction = Transaction.builder()
                .sourceWallet(senderWallet)
                .destinationWallet(receiverWallet)
                .txReference("TXN-TRANSFER_002")
                .amount(new BigDecimal("300.00"))
                .transactionType(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .narration("Wallet transfer")
                .build();
//  Act
        TransactionResponse response =
                transactionMapper.mapToResponse(
                        transaction,
                        receiverWalletId
                );

//  Assert
        assertEquals(TransactionDirection.INCOMING,
                response.direction());
    }

    @Test
    void shouldMapDepositAsIncoming(){

//        Arrange
        Long walletId = 1L;

        User user = User.builder()
                .fullName("Deposit User")
                .email("user" + UUID.randomUUID() + "@gmail.com")
                .phoneNumber("080" + String.format("%08d",
                        new Random().nextInt(100_000_000)))
                .build();

        Wallet wallet = Wallet.createFor(user);

        ReflectionTestUtils.setField(wallet, "id", walletId);

        Transaction transaction = Transaction.builder()
                .sourceWallet(null)
                .destinationWallet(wallet)
                .txReference("TXN-DEPOSIT-001")
                .amount(new BigDecimal("500.00"))
                .transactionType(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .narration("Wallet Deposit")
                .build();

//        Act
        var response = transactionMapper.
                mapToResponse(transaction, walletId);

//        Assert
        assertEquals(TransactionDirection.INCOMING, response.direction());
    }
}