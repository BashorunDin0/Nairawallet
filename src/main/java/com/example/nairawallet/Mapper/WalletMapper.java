package com.example.nairawallet.Mapper;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.example.nairawallet.Dto.Response.WalletResponse;
import com.example.nairawallet.Entity.Wallet;
import org.springframework.stereotype.Component;

@Component
public class WalletMapper {
    public WalletResponse mapToResponse(Wallet wallet){
        return new WalletResponse(
                wallet.getId(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getStatus());
    }
}
