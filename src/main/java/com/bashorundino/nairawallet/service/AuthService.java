package com.bashorundino.nairawallet.service;

import com.bashorundino.nairawallet.dto.request.LoginRequest;

public interface AuthService {
    String login(LoginRequest request);
}
