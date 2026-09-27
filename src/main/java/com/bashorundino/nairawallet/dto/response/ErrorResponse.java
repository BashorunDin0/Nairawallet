package com.bashorundino.nairawallet.dto.response;

import java.time.Instant;
import java.util.Map;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */
public record ErrorResponse(

        Instant timestamp,
        int status,
        String error,
        String path,
        Map<String, String> details

) {
}
