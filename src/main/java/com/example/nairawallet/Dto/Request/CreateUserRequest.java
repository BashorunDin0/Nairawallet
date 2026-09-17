package com.example.nairawallet.Dto.Request;

/*
 * Copyright (c) 2026. [Yusuff I. Olawale/BashorunDIn0].
 * All rights reserved.
 * This project was developed as part of a Fintech MVP series.
 */

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank(message = "fullName is required")
        String fullName,
        @Email(message = "email must be valid")
        String email,
        @NotBlank
        String phoneNumber

) {
        public CreateUserRequest{
                email = email == null ? null : email.trim().toLowerCase();
        }
}
