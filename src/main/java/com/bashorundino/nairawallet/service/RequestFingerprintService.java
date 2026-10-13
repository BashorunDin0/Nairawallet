package com.bashorundino.nairawallet.service;


import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class RequestFingerprintService {

    public String forDeposit(Long walletId, BigDecimal amount){

        String payload = "DEPOSIT|"
                + walletId
                + "|"
                + amount.stripTrailingZeros().toPlainString();

       return sha256(payload);
    }

    public String forWithdrawal(Long walletId, BigDecimal amount) {
        String payload = "WITHDRAWAL|"
                + walletId
                + "|"
                + amount.stripTrailingZeros().toPlainString();

        return hash(payload);
    }

    public String forTransfer(
            Long senderWalletId,
            Long receiverWalletId,
            BigDecimal amount) {

        String payload = "TRANSFER|"
                + senderWalletId
                + "|"
                + receiverWalletId
                + "|"
                + amount.stripTrailingZeros().toPlainString();

        return hash(payload);
    }

    private String hash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    payload.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(
                    payload.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashBytes);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }
}
