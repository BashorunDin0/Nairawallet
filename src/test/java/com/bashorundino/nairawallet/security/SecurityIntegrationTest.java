package com.bashorundino.nairawallet.security;


import com.bashorundino.nairawallet.dto.request.CreateUserRequest;
import com.bashorundino.nairawallet.dto.request.LoginRequest;
import com.bashorundino.nairawallet.entity.User;
import com.bashorundino.nairawallet.repository.UserRepository;
import com.bashorundino.nairawallet.repository.WalletRepository;
import com.bashorundino.nairawallet.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldRejectProtectedEndpointWithoutJwt() throws Exception {

        mockMvc.perform(
                        get("/api/v1/transactions/wallet/999")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAuthenticatedUserFromAccessingAnotherUsersWallet()
            throws Exception {

        // Arrange - create User A
        String userAEmail =
                "usera-" + UUID.randomUUID() + "@gmail.com";

        String userAPhone =
                "071" + UUID.randomUUID()
                        .toString()
                        .replaceAll("\\D", "")
                        .substring(0, 8);

        CreateUserRequest userARequest = new CreateUserRequest(
                "User A",
                userAEmail,
                "password123",
                userAPhone

        );

        userService.createUser(userARequest);

        // Create User B
        String userBEmail =
                "userb-" + UUID.randomUUID() + "@gmail.com";

        String userBPhone =
                "091" + UUID.randomUUID()
                        .toString()
                        .replaceAll("\\D", "")
                        .substring(0, 8);

        CreateUserRequest userBRequest = new CreateUserRequest(
                "User B",
                userBEmail,
                "password123",
                userBPhone

        );

        userService.createUser(userBRequest);

        // Find User B's wallet
        User userB = userRepository
                .findByEmail(userBEmail)
                .orElseThrow();

        Long userBWalletId = userB.getWallet().getId();

        // Login as User A
        String loginResponse = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "email": "%s",
                                        "password": "password123"
                                    }
                                    """.formatted(userAEmail))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Act & Assert
        mockMvc.perform(
                        get("/api/v1/transactions/wallet/" + userBWalletId)
                                .header(
                                        "Authorization",
                                        "Bearer " + loginResponse
                                )
                )
                .andExpect(status().isForbidden());
    }

}
