package com.example.nairawallet.Dto.Response;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

public record UserResponse(
        Long id,
        String fullName,
        String email,
        Long walletId
) {
}
