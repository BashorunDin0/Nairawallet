package com.bashorundino.nairawallet.dto.response;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.enums.Currency;
import com.bashorundino.nairawallet.enums.WalletStatus;

import java.math.BigDecimal;

public record WalletResponse(
        Long walletId,
        BigDecimal balance,
        Currency currency,
        WalletStatus status

) {
}
