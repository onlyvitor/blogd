package com.onlyvitor.blogd.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.onlyvitor.blogd.user.repository.UserRepository;
import com.onlyvitor.blogd.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "StrongPass1!";
    private static final String NEW_PASSWORD = "NewStrong2@";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    private final List<String> createdUsernames = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdUsernames.forEach(username ->
                userRepository.findByUsername(username).ifPresent(user -> userService.delete(user.getId())));
    }

    @Test
    void shouldSupportFullAuthenticationLifecycle() throws Exception {
        String username = uniqueUsername("alice");

        long userId = register(username);

        JsonNode login = login(username, PASSWORD);
        String accessToken = login.get("accessToken").asText();
        String refreshToken = login.get("refreshToken").asText();

        assertThat(login.get("user").get("username").asText()).isEqualTo(username);
        assertThat(login.get("user").get("role").asText()).isEqualTo("USUARIO");

        mockMvc.perform(get("/api/users/{id}", userId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        mockMvc.perform(get("/api/users/{id}", userId))
                .andExpect(status().isUnauthorized());

        JsonNode refreshed = refresh(refreshToken);
        String newRefreshToken = refreshed.get("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);
        assertThat(refreshed.get("accessToken").asText()).isNotBlank();

        // Reusing a rotated token invalidates the whole session family.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(newRefreshToken)))
                .andExpect(status().isUnauthorized());

        JsonNode secondLogin = login(username, PASSWORD);
        String secondRefreshToken = secondLogin.get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(secondRefreshToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(secondRefreshToken)))
                .andExpect(status().isUnauthorized());

        JsonNode thirdLogin = login(username, PASSWORD);
        String thirdAccessToken = thirdLogin.get("accessToken").asText();

        mockMvc.perform(put("/api/users/{id}/password", userId)
                        .header("Authorization", "Bearer " + thirdAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", PASSWORD,
                                "newPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login").with(httpBasic(username, PASSWORD)))
                .andExpect(status().isUnauthorized());
        login(username, NEW_PASSWORD);

        String otherUsername = uniqueUsername("bob");
        long otherUserId = register(otherUsername);
        String otherToken = login(otherUsername, PASSWORD).get("accessToken").asText();

        mockMvc.perform(put("/api/users/{id}/password", userId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", PASSWORD,
                                "newPassword", NEW_PASSWORD))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/users/{id}", otherUserId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldRejectWrongCurrentPassword() throws Exception {
        String username = uniqueUsername("carol");
        long userId = register(username);
        String accessToken = login(username, PASSWORD).get("accessToken").asText();

        mockMvc.perform(put("/api/users/{id}/password", userId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "WrongPass1!",
                                "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Current password is incorrect"));
    }

    @Test
    void shouldNotAllowPublicRegistrationAsAdmin() throws Exception {
        String username = uniqueUsername("mallory");
        createdUsernames.add(username);

        mockMvc.perform(post("/api/users/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", PASSWORD,
                                "email", username + "@example.com",
                                "role", "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USUARIO"));
    }

    private long register(String username) throws Exception {
        createdUsernames.add(username);

        MvcResult result = mockMvc.perform(post("/api/users/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", PASSWORD,
                                "email", username + "@example.com"))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(httpBasic(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String refreshBody(String refreshToken) throws Exception {
        return objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken));
    }

    private String uniqueUsername(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
