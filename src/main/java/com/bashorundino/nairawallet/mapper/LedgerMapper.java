package com.bashorundino.nairawallet.mapper;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import com.bashorundino.nairawallet.dto.response.LedgerEntryResponse;
import com.bashorundino.nairawallet.entity.LedgerEntry;
import org.springframework.stereotype.Component;

@Component
public class LedgerMapper {
    public LedgerEntryResponse mapToResponse(LedgerEntry ledger){
        return new LedgerEntryResponse(
                ledger.getAmount(),
                ledger.getEntryType(),
                ledger.getBalanceBefore(),
                ledger.getBalanceAfter(),
                ledger.getNarration(),
                ledger.getCreatedAt()
        );
    }
}
