package com.bashorundino.nairawallet.service;

import com.bashorundino.nairawallet.dto.request.LoginRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    public String login(LoginRequest request) {

        String email = request.email().trim().toLowerCase();

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        email, request.password());

        authenticationManager.authenticate(authentication);

        return jwtService.generateToken(email);


    }
}
