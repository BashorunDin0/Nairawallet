package com.bashorundino.nairawallet.service;


import com.bashorundino.nairawallet.dto.request.TransferRequest;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.entity.Wallet;
import com.bashorundino.nairawallet.exception.InactiveWalletException;
import com.bashorundino.nairawallet.repository.UserRepository;
import com.bashorundino.nairawallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class TransactionServiceImpIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    void shouldRollbackTransferWhenReceiverIsInactive() {

//        Arrange
        User sender = User.builder()
                .fullName("Integration")
                .email("integrate@gmail.com")
                .phoneNumber("000")
                .build();

        Wallet senderWallet = Wallet.createFor(sender);
        senderWallet.credit(new BigDecimal("1000.00"));

        User receiver = User.builder()
                .fullName("receiver")
                .email("receiver@gmail.com")
                .phoneNumber("111")
                .build();

        Wallet receiverWallet = Wallet.createFor(receiver);
        receiverWallet.credit(new BigDecimal("500.00"));
        receiverWallet.deactivate();

        userRepository.save(sender);
        userRepository.save(receiver);

        Long senderWalletId = senderWallet.getId();
        Long receiverWalletId = receiverWallet.getId();

        TransferRequest request = new TransferRequest(
                senderWalletId,
                receiverWalletId,
                new BigDecimal("300.00"),
                "Transfer"
        );
//  Act & Assert
        assertThrows(InactiveWalletException.class,
                () -> transactionService.transfer(request,
                        "Integration-transfer-001"));

//        Reload wallets from database
        Wallet senderAfter = walletRepository.
                findById(senderWalletId).orElseThrow();

        Wallet receiverAfter = walletRepository.
                findById(receiverWalletId).orElseThrow();

//  Verify and rollback

        assertEquals(new BigDecimal("1000.00"),
                senderAfter.getBalance());

        assertEquals(new BigDecimal("500.00"),
                receiverAfter.getBalance());

    }
}
