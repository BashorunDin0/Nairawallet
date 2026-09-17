package com.example.nairawallet.Dto.Response;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.example.nairawallet.Enum.Currency;
import com.example.nairawallet.Enum.TransactionStatus;
import com.example.nairawallet.Enum.WalletStatus;

import java.math.BigDecimal;

public record WalletResponse(
        Long walletId,
        BigDecimal balance,
        Currency currency,
        WalletStatus status

) {
}
