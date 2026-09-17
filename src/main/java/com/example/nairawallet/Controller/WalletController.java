package com.example.nairawallet.Controller;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.example.nairawallet.Dto.Response.WalletResponse;
import com.example.nairawallet.Service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;

    @GetMapping("/{walletId}")
    public ResponseEntity<WalletResponse> getWallet(@Valid @PathVariable Long walletId){
        WalletResponse response = walletService.getWallet(walletId);

        return ResponseEntity.ok().body(response);

    }
}
