package com.bashorundino.nairawallet.service.unit;


import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.entity.Transaction;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.enums.TransactionDirection;
import com.bashorundino.nairawallet.mapper.TransactionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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

        User sender = createUser(
                "Mapper test" + UUID.randomUUID(),
                "sendermapper",
                "080"
                );

        User receiver = createUser(
                "Mapper receiver" + UUID.randomUUID(),
                "receivermapper",
                "080"
                );

        Wallet senderWallet = Wallet.createFor(sender);
        Wallet receiverWallet = Wallet.createFor(receiver);

        ReflectionTestUtils.setField(senderWallet, "id", senderWalletId);
        ReflectionTestUtils.setField(receiverWallet, "id", receiverWalletId);

        Transaction transaction = Transaction.createTransfer(
                "TXN-TRANSFER_001",
                new BigDecimal("300.00"),
                "Wallet transfer",
                senderWallet,
                receiverWallet
                );
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

        User sender = createUser(
                "Mapper test2" + UUID.randomUUID(),
                "sendermapper2",
                "080"
                );

        User receiver = createUser(
                "Mapper receiver2" + UUID.randomUUID(),
                "receivermapper2",
                "080"
                );

        Wallet senderWallet = Wallet.createFor(sender);
        Wallet receiverWallet = Wallet.createFor(receiver);

        ReflectionTestUtils.setField(senderWallet, "id", senderWalletId);
        ReflectionTestUtils.setField(receiverWallet, "id", receiverWalletId);

        Transaction transaction = Transaction.createTransfer(
                        "TXN-TRANSFER_002",
                        new BigDecimal("300.00"),
                        "Wallet transfer",
                        senderWallet,
                        receiverWallet
                );
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

        User user = createUser(
                "Deposit User" + UUID.randomUUID(),
                "user",
                "080"
                );

        Wallet wallet = Wallet.createFor(user);

        ReflectionTestUtils.setField(wallet, "id", walletId);

        Transaction transaction = Transaction.createDeposit(
                        "TXN-DEPOSIT-001",
                        new BigDecimal("500.00"),
                        "Wallet Deposit",
                        wallet
                );

//        Act
        var response = transactionMapper.
                mapToResponse(transaction, walletId);

//        Assert
        assertEquals(TransactionDirection.INCOMING, response.direction());
    }

    private User createUser(
            String fullName,
            String emailPrefix,
            String phonePrefix
    ) {
        return User.create(
                fullName,
                emailPrefix + UUID.randomUUID() + "@gmail.com",
                "password123",
                phonePrefix + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
        );
    }
}