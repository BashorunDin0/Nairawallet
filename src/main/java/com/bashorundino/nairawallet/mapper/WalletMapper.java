package com.bashorundino.nairawallet.mapper;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.dto.response.WalletResponse;
import com.bashorundino.nairawallet.entity.Wallet;
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
