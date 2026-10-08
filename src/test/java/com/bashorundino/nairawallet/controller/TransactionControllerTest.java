package com.bashorundino.nairawallet.controller;

import com.bashorundino.nairawallet.dto.request.DepositRequest;
import com.bashorundino.nairawallet.dto.request.TransferRequest;
import com.bashorundino.nairawallet.dto.request.WithdrawRequest;
import com.bashorundino.nairawallet.dto.response.TransactionResponse;
import com.bashorundino.nairawallet.enums.TransactionDirection;
import com.bashorundino.nairawallet.enums.TransactionStatus;
import com.bashorundino.nairawallet.enums.TransactionType;
import com.bashorundino.nairawallet.service.transaction.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(TransactionController.class)
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void shouldRejectNegativeDepositAmount() throws Exception{

        String request = """
                {
                "walletId": 8,
                "amount": -500
                }
                """;

        mockMvc.perform(
                post("/api/v1/transactions/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-key", "test key")
                        .content(request)
        )
                .andExpect(status().isBadRequest());


    }

    @Test
    void shouldRejectDepositWhenWalletIdIsMissing() throws Exception {

        String request = """
            {
                "amount": 500
            }
            """;

        mockMvc.perform(
                        post("/api/v1/transactions/deposit")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-key", "test key")
                                .content(request)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectNegativeWithdrawalAmount() throws Exception {

        String request = """
            {
                "walletId": 1,
                "amount": -500
            }
            """;

        mockMvc.perform(
                        post("/api/v1/transactions/withdraw")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-key", "test key")
                                .content(request)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectTransferWhenSenderWalletIdIsMissing() throws Exception {

        String request = """
            {
                "receiverWalletId": 2,
                "amount": 500
            }
            """;

        mockMvc.perform(
                        post("/api/v1/transactions/transfer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-key", "test key")
                                .content(request)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectTransferWhenReceiverWalletIdIsNegative() throws  Exception{

        String request = """
                {
                    "senderWalletId": 8,
                    "receiverWalletId": -1,
                    "amount": 500
                }
                """;
        mockMvc.perform(
                post("/api/v1/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-key", "test key")
                        .content(request)
        )
                .andExpect(status().isBadRequest());

    }

    @Test
    void shouldRejectTransferWhenAmountHasMoreThanTwoDecimalPlaces() throws Exception{
        String request = """
                {
                    "senderWalletId": 8,
                    "receiverWalletId": 1,
                    "amount": 500.000
                }
                """;
        mockMvc.perform(
                post("/api/v1/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-key", "test key")
                        .content(request)
        )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldProcessValidDeposit() throws Exception {

        String request = """
            {
                "walletId": 1,
                "amount": 500
            }
            """;
        TransactionResponse expectedResponse = new TransactionResponse(
                "TXN-123",
                new BigDecimal("500.00"),
                TransactionStatus.SUCCESS,
                TransactionType.DEPOSIT,
                "Wallet deposit",
                Instant.parse("2026-10-08T10:00:00Z"),
                TransactionDirection.INCOMING
        );

        when(transactionService.deposit(any(DepositRequest.class), eq("test key")))
                .thenReturn(expectedResponse);

        mockMvc.perform(
                        post("/api/v1/transactions/deposit")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-key", "test key")
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("TXN-123"))
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.narration").value("Wallet deposit"))
                .andExpect(jsonPath("$.direction").value("INCOMING"));

        verify(transactionService).deposit(any(DepositRequest.class), eq("test key"));
    }

    @Test
    void shouldProcessValidWithdrawal() throws Exception {

        String request = """
            {
                "walletId": 1,
                "amount": 500
            }
            """;

        mockMvc.perform(
                        post("/api/v1/transactions/withdraw")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-key", "test key")
                                .content(request)
                )
                .andExpect(status().isOk());

        verify(transactionService)
                .withdrawal(any(WithdrawRequest.class), eq("test key"));
    }

    @Test
    void shouldProcessValidTransfer() throws Exception {

        String request = """
            {
                "senderWalletId": 1,
                "receiverWalletId": 2,
                "amount": 500
            }
            """;

        mockMvc.perform(
                        post("/api/v1/transactions/transfer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-key", "test key")
                                .content(request)
                )
                .andExpect(status().isOk());

        verify(transactionService)
                .transfer(any(TransferRequest.class), eq("test key"));
    }

    @Test
    void shouldRejectNegativePage() throws Exception{
        mockMvc.perform(
                get("/api/v1/transactions/wallet/8")
                        .param("page", "-1")
                        .param("size", "20")
        )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectPageSizeGreaterThan100() throws Exception {

        mockMvc.perform(
                        get("/api/v1/transactions/wallet/8")
                                .param("page", "0")
                                .param("size", "101")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldProcessValidTransferAndReturnResponse() throws Exception {

        String request = """
            {
                "senderWalletId": 1,
                "receiverWalletId": 2,
                "amount": 500,
                "narration": "Payment for goods"
            }
            """;

        TransactionResponse expectedResponse = new TransactionResponse(
                "TXN-456",
                new BigDecimal("500.00"),
                TransactionStatus.SUCCESS,
                TransactionType.TRANSFER,
                "Payment for goods",
                Instant.parse("2026-10-08T10:00:00Z"),
                TransactionDirection.OUTGOING
        );

        when(transactionService.transfer(any(TransferRequest.class), eq("test key")))
                .thenReturn(expectedResponse);

        mockMvc.perform(
                        post("/api/v1/transactions/transfer")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-key", "test key")
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("TXN-456"))
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.narration").value("Payment for goods"))
                .andExpect(jsonPath("$.direction").value("OUTGOING"));

        verify(transactionService)
                .transfer(any(TransferRequest.class), eq("test key"));
    }

    @Test
    void shouldRejectDepositWhenIdempotencyKeyIsMissing() throws Exception {

        String request = """
            {
                "walletId": 8,
                "amount": 500
            }
            """;

        mockMvc.perform(
                        post("/api/v1/transactions/deposit")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest());
    }
}
