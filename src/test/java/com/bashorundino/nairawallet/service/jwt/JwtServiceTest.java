package com.bashorundino.nairawallet.service.jwt;

import com.bashorundino.nairawallet.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JwtServiceTest {

        private JwtService jwtService;

        @BeforeEach
        void setUp() {

            jwtService = new JwtService(
                    "nairawallet-dev-jwt-secret-2026-this-is-long-enough-for-hs384",
                    3600000
            );
        }

        @Test
        void shouldGenerateAndExtractEmailFromToken() {

            String email = "test@example.com";

            String token = jwtService.generateToken(email);

            String extractedEmail = jwtService.extractEmail(token);

            assertEquals(email, extractedEmail);
        }

        @Test
        void shouldValidateValidToken() {

            String email = "test@example.com";

            String token = jwtService.generateToken(email);

            assertTrue(jwtService.isTokenValid(token, email));
        }

        @Test
        void shouldRejectTokenForDifferentEmail() {

            String token =
                    jwtService.generateToken("test@example.com");

            assertFalse(
                    jwtService.isTokenValid(
                            token,
                            "another@example.com"
                    )
            );
        }
    }
