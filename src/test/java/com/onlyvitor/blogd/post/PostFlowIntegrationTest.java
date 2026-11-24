package com.onlyvitor.blogd.post;

import com.onlyvitor.blogd.post.repository.PostRepository;
import com.onlyvitor.blogd.user.domain.Role;
import com.onlyvitor.blogd.user.dto.UserRequest;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
class PostFlowIntegrationTest {

    private static final String PASSWORD = "StrongPass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    private final List<String> createdUsernames = new ArrayList<>();
    private final List<Long> createdPostIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdPostIds.forEach(id -> {
            if (postRepository.existsById(id)) {
                postRepository.deleteById(id);
            }
        });
        createdUsernames.forEach(username ->
                userRepository.findByUsername(username).ifPresent(user -> userService.delete(user.getId())));
    }

    @Test
    void shouldCreateReadUpdateAndDeletePostsWithAuthorsRelationsAndAttachments() throws Exception {
        Account alice = account("alice");
        Account bob = account("bob");
        Account carol = account("carol");

        JsonNode first = createPost(alice, Map.of(
                "title", "Primeiro post",
                "content", "# Olá\n\nTexto em **markdown**",
                "authorIds", List.of(alice.id()),
                "attachments", List.of(
                        Map.of("type", "IMAGE", "url", "https://example.com/cover.png", "name", "Capa"),
                        Map.of("type", "FILE", "url", "https://example.com/doc.pdf"))));
        long firstId = first.get("id").asLong();

        assertThat(first.get("format").asText()).isEqualTo("MARKDOWN");
        assertThat(first.get("createdAt").asText()).isNotBlank();
        assertThat(first.get("authors").size()).isEqualTo(1);
        assertThat(first.get("authors").get(0).get("username").asText()).isEqualTo(alice.username());
        assertThat(first.get("relatedPosts").size()).isZero();
        assertThat(first.get("attachments").size()).isEqualTo(2);
        assertThat(first.get("attachments").get(0).get("type").asText()).isEqualTo("IMAGE");

        JsonNode second = createPost(bob, Map.of(
                "title", "Segundo post",
                "content", "Título\n======\n\nTexto em reStructuredText",
                "format", "RST",
                "authorIds", List.of(alice.id(), bob.id()),
                "relatedPostIds", List.of(firstId),
                "attachments", List.of(Map.of("type", "VIDEO", "url", "https://example.com/video.mp4"))));
        long secondId = second.get("id").asLong();

        assertThat(second.get("format").asText()).isEqualTo("RST");
        assertThat(second.get("authors").size()).isEqualTo(2);
        assertThat(second.get("relatedPosts").size()).isEqualTo(1);
        assertThat(second.get("relatedPosts").get(0).get("id").asLong()).isEqualTo(firstId);
        assertThat(second.get("relatedPosts").get(0).get("title").asText()).isEqualTo("Primeiro post");

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(carol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Sem permissão",
                                "content", "Conteúdo",
                                "authorIds", List.of(alice.id())))))
                .andExpect(status().isForbidden());

        assertThat(ids(mockMvc.perform(get("/api/posts").header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andReturn())).doesNotHaveDuplicates().contains(firstId, secondId);

        mockMvc.perform(get("/api/posts/{id}", secondId).header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Segundo post"));

        mockMvc.perform(get("/api/posts/{id}", -1L).header("Authorization", bearer(alice)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Post not found with id: -1"));

        JsonNode firstUpdated = read(mockMvc.perform(put("/api/posts/{id}", firstId)
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Primeiro post revisado",
                                "content", "Conteúdo revisado",
                                "format", "RST",
                                "authorIds", List.of(alice.id(), bob.id()),
                                "relatedPostIds", List.of(secondId),
                                "attachments", List.of(Map.of("type", "IMAGE", "url", "https://example.com/new-cover.png"))))))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(firstUpdated.get("title").asText()).isEqualTo("Primeiro post revisado");
        assertThat(firstUpdated.get("format").asText()).isEqualTo("RST");
        assertThat(firstUpdated.get("authors").size()).isEqualTo(2);
        assertThat(firstUpdated.get("relatedPosts").get(0).get("id").asLong()).isEqualTo(secondId);
        assertThat(firstUpdated.get("attachments").size()).isEqualTo(1);
        assertThat(firstUpdated.get("attachments").get(0).get("url").asText())
                .isEqualTo("https://example.com/new-cover.png");

        mockMvc.perform(put("/api/posts/{id}", firstId)
                        .header("Authorization", bearer(carol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Invasão",
                                "content", "Conteúdo",
                                "authorIds", List.of(carol.id())))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/posts/{id}", firstId)
                        .header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Removendo a mim mesmo",
                                "content", "Conteúdo",
                                "authorIds", List.of(alice.id())))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/posts/{id}", firstId).header("Authorization", bearer(carol)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/posts/{id}", firstId).header("Authorization", bearer(alice)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/posts/{id}", firstId).header("Authorization", bearer(alice)))
                .andExpect(status().isNotFound());

        JsonNode secondAfterDelete = read(mockMvc.perform(get("/api/posts/{id}", secondId)
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(secondAfterDelete.get("relatedPosts").size()).isZero();
    }

    @Test
    void shouldRejectInvalidPostPayloads() throws Exception {
        Account alice = account("alice");

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", " ",
                                "content", "Conteúdo",
                                "authorIds", List.of(alice.id())))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Title is required"));

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Título",
                                "content", "Conteúdo",
                                "authorIds", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.authorIds").value("At least one author is required"));

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Título",
                                "content", "Conteúdo",
                                "authorIds", List.of(alice.id()),
                                "attachments", List.of(Map.of("type", "FILE", "url", "javascript:alert(1)"))))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Título",
                                "content", "Conteúdo",
                                "authorIds", List.of(alice.id(), -1L)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found with id: -1"));

        JsonNode created = createPost(alice, Map.of(
                "title", "Post",
                "content", "Conteúdo",
                "authorIds", List.of(alice.id())));
        long postId = created.get("id").asLong();

        mockMvc.perform(put("/api/posts/{id}", postId)
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Post",
                                "content", "Conteúdo",
                                "authorIds", List.of(alice.id()),
                                "relatedPostIds", List.of(postId)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A post cannot be related to itself"));

        mockMvc.perform(put("/api/posts/{id}", postId)
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Post",
                                "content", "Conteúdo",
                                "authorIds", List.of(alice.id()),
                                "relatedPostIds", List.of(-1L)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Post not found with id: -1"));
    }

    @Test
    void shouldAllowAdminToManageAnyPost() throws Exception {
        Account author = account("author");
        Account admin = admin("admin");

        JsonNode created = createPost(author, Map.of(
                "title", "Post do autor",
                "content", "Conteúdo",
                "authorIds", List.of(author.id())));
        long postId = created.get("id").asLong();

        JsonNode updated = read(mockMvc.perform(put("/api/posts/{id}", postId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Post editado pelo admin",
                                "content", "Conteúdo",
                                "authorIds", List.of(admin.id())))))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(updated.get("authors").size()).isEqualTo(1);
        assertThat(updated.get("authors").get(0).get("id").asLong()).isEqualTo(admin.id());

        mockMvc.perform(delete("/api/posts/{id}", postId).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldKeepPostsWhenCoAuthorIsDeleted() throws Exception {
        Account alice = account("alice");
        Account dave = account("dave");

        long postId = createPost(alice, Map.of(
                "title", "Coautoria",
                "content", "Conteúdo",
                "authorIds", List.of(alice.id(), dave.id()))).get("id").asLong();

        userService.delete(dave.id());

        JsonNode found = read(mockMvc.perform(get("/api/posts/{id}", postId).header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(found.get("authors").size()).isEqualTo(1);
        assertThat(found.get("authors").get(0).get("id").asLong()).isEqualTo(alice.id());
    }

    private Account account(String prefix) throws Exception {
        String username = uniqueUsername(prefix);
        createdUsernames.add(username);

        MvcResult registration = mockMvc.perform(post("/api/users/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "password", PASSWORD,
                                "email", username + "@example.com"))))
                .andExpect(status().isCreated())
                .andReturn();

        long id = read(registration).get("id").asLong();
        return new Account(id, username, login(username));
    }

    private Account admin(String prefix) throws Exception {
        String username = uniqueUsername(prefix);
        createdUsernames.add(username);

        long id = userService.create(UserRequest.builder()
                .username(username)
                .password(PASSWORD)
                .email(username + "@example.com")
                .role(Role.ADMIN)
                .build(), true).id();

        return new Account(id, username, login(username));
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(httpBasic(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return read(result).get("accessToken").asText();
    }

    private JsonNode createPost(Account account, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = read(result);
        createdPostIds.add(created.get("id").asLong());
        return created;
    }

    private List<Long> ids(MvcResult result) throws Exception {
        JsonNode posts = read(result);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < posts.size(); i++) {
            ids.add(posts.get(i).get("id").asLong());
        }
        return ids;
    }

    private String bearer(Account account) {
        return "Bearer " + account.token();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String uniqueUsername(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private record Account(long id, String username, String token) {
    }
}
