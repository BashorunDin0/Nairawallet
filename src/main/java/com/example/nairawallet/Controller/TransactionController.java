package com.example.nairawallet.Controller;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.example.nairawallet.Dto.Request.DepositRequest;
import com.example.nairawallet.Dto.Request.TransferRequest;
import com.example.nairawallet.Dto.Request.WithdrawRequest;
import com.example.nairawallet.Dto.Response.TransactionResponse;
import com.example.nairawallet.Dto.Response.WalletResponse;
import com.example.nairawallet.Service.TransactionService;
import com.example.nairawallet.Service.UserService;
import com.example.nairawallet.Service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@Validated
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;



    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody DepositRequest request,
                                                  @RequestHeader("Idempotency-key") String key){
        TransactionResponse response = transactionService.deposit(request, key);
        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(@Valid @RequestBody WithdrawRequest request,
                                                   @RequestHeader("Idempotency-key") String key){
        TransactionResponse response = transactionService.withdrawal(request, key);

        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transferFunds(@Valid @RequestBody TransferRequest request,
                                                             @RequestHeader("Idempotency-key") String key){
        TransactionResponse response = transactionService.transfer(request, key);

        return ResponseEntity.ok().body(response);
    }

    @GetMapping("{walletId}")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @PathVariable Long walletId,
            @RequestParam(defaultValue= "0")
            @Min(value = 0, message = "page number cannot be negative")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "page size must be at least 1")
            @Max(value = 100, message = "page size cannot exceed 100")
            int size){
        Page<TransactionResponse> response = transactionService.getTransactions(walletId, page, size );
        return ResponseEntity.ok().body(response);
    }
}
