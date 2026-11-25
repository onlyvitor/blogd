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
    void shouldCreateReadUpdateAndDeletePostsWithAuthorCoauthorsRelationsAndAttachments() throws Exception {
        Account alice = account("alice");
        Account bob = account("bob");
        Account carol = account("carol");

        JsonNode first = createPost(alice, Map.of(
                "title", "Primeiro post",
                "content", "# Olá\n\nTexto em **markdown**",
                "authorId", alice.id(),
                "attachments", List.of(
                        Map.of("type", "IMAGE", "url", "https://example.com/cover.png", "name", "Capa"),
                        Map.of("type", "FILE", "url", "https://example.com/doc.pdf"))));
        long firstId = first.get("id").asLong();

        assertThat(first.get("format").asText()).isEqualTo("MARKDOWN");
        assertThat(first.get("visibility").asText()).isEqualTo("PUBLIC");
        assertThat(first.get("key").isNull()).isTrue();
        assertThat(first.get("createdAt").asText()).isNotBlank();
        assertThat(first.get("author").get("username").asText()).isEqualTo(alice.username());
        assertThat(first.get("coauthors").size()).isZero();
        assertThat(first.get("deletable").asBoolean()).isFalse();
        assertThat(first.get("relatedPosts").size()).isZero();
        assertThat(first.get("attachments").size()).isEqualTo(2);
        assertThat(first.get("attachments").get(0).get("type").asText()).isEqualTo("IMAGE");

        JsonNode second = createPost(bob, Map.of(
                "title", "Segundo post",
                "content", "Título\n======\n\nTexto em reStructuredText",
                "format", "RST",
                "authorId", bob.id(),
                "coauthorIds", List.of(alice.id()),
                "relatedPostIds", List.of(firstId),
                "attachments", List.of(Map.of("type", "VIDEO", "url", "https://example.com/video.mp4"))));
        long secondId = second.get("id").asLong();

        assertThat(second.get("format").asText()).isEqualTo("RST");
        assertThat(second.get("author").get("id").asLong()).isEqualTo(bob.id());
        assertThat(second.get("coauthors").size()).isEqualTo(1);
        assertThat(second.get("coauthors").get(0).get("id").asLong()).isEqualTo(alice.id());
        assertThat(second.get("relatedPosts").size()).isEqualTo(1);
        assertThat(second.get("relatedPosts").get(0).get("id").asLong()).isEqualTo(firstId);
        assertThat(second.get("relatedPosts").get(0).get("title").asText()).isEqualTo("Primeiro post");

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(carol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Sem permissão",
                                "content", "Conteúdo",
                                "authorId", alice.id()))))
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
                                "authorId", alice.id(),
                                "coauthorIds", List.of(bob.id()),
                                "relatedPostIds", List.of(secondId),
                                "attachments", List.of(Map.of("type", "IMAGE", "url", "https://example.com/new-cover.png"))))))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(firstUpdated.get("title").asText()).isEqualTo("Primeiro post revisado");
        assertThat(firstUpdated.get("format").asText()).isEqualTo("RST");
        assertThat(firstUpdated.get("author").get("id").asLong()).isEqualTo(alice.id());
        assertThat(firstUpdated.get("coauthors").get(0).get("id").asLong()).isEqualTo(bob.id());
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
                                "authorId", carol.id()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/posts/{id}", firstId)
                        .header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Removendo a mim mesmo",
                                "content", "Conteúdo",
                                "authorId", alice.id()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/posts/{id}", firstId)
                        .header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Coautor trocando autores",
                                "content", "Conteúdo",
                                "authorId", alice.id(),
                                "coauthorIds", List.of(bob.id(), carol.id())))))
                .andExpect(status().isForbidden());

        JsonNode updatedByCoauthor = read(mockMvc.perform(put("/api/posts/{id}", firstId)
                        .header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Revisado pelo coautor",
                                "content", "Conteúdo",
                                "authorId", alice.id(),
                                "coauthorIds", List.of(bob.id())))))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(updatedByCoauthor.get("title").asText()).isEqualTo("Revisado pelo coautor");

        mockMvc.perform(delete("/api/posts/{id}", firstId).header("Authorization", bearer(carol)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/posts/{id}", firstId).header("Authorization", bearer(alice)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());

        JsonNode accepted = acceptDeletion(alice, firstId);
        assertThat(accepted.get("deletionAcceptances").size()).isEqualTo(1);
        assertThat(accepted.get("deletable").asBoolean()).isTrue();

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
    void shouldDeletePostOnlyWhenMajorityOfAuthorsAccept() throws Exception {
        Account alice = account("alice");
        Account bob = account("bob");
        Account carol = account("carol");

        long postId = createPost(alice, Map.of(
                "title", "Votação",
                "content", "Conteúdo",
                "authorId", alice.id(),
                "coauthorIds", List.of(bob.id(), carol.id()))).get("id").asLong();

        mockMvc.perform(delete("/api/posts/{id}", postId).header("Authorization", bearer(alice)))
                .andExpect(status().isConflict());

        assertThat(acceptDeletion(alice, postId).get("deletable").asBoolean()).isFalse();

        JsonNode bobAccepted = acceptDeletion(bob, postId);
        assertThat(bobAccepted.get("deletable").asBoolean()).isTrue();
        assertThat(bobAccepted.get("deletionAcceptances").size()).isEqualTo(2);

        mockMvc.perform(delete("/api/posts/{id}", postId).header("Authorization", bearer(carol)))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldLetMainAuthorDecideTiesAndAllowWithdrawingAcceptance() throws Exception {
        Account alice = account("alice");
        Account bob = account("bob");

        long postId = createPost(alice, Map.of(
                "title", "Empate",
                "content", "Conteúdo",
                "authorId", alice.id(),
                "coauthorIds", List.of(bob.id()))).get("id").asLong();

        assertThat(acceptDeletion(bob, postId).get("deletable").asBoolean()).isFalse();

        mockMvc.perform(delete("/api/posts/{id}", postId).header("Authorization", bearer(bob)))
                .andExpect(status().isConflict());

        JsonNode withdrawn = read(mockMvc.perform(delete("/api/posts/{id}/deletion-acceptance", postId)
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(withdrawn.get("deletionAcceptances").size()).isZero();

        assertThat(acceptDeletion(alice, postId).get("deletable").asBoolean()).isTrue();

        mockMvc.perform(delete("/api/posts/{id}", postId).header("Authorization", bearer(bob)))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldShowPrivatePostsOnlyToAuthorsAndKeyHolders() throws Exception {
        Account alice = account("alice");
        Account bob = account("bob");
        Account carol = account("carol");

        JsonNode created = createPost(alice, Map.of(
                "title", "Segredo",
                "content", "Conteúdo privado",
                "visibility", "PRIVATE",
                "authorId", alice.id(),
                "coauthorIds", List.of(bob.id())));
        long postId = created.get("id").asLong();
        String key = created.get("key").asText();

        assertThat(created.get("visibility").asText()).isEqualTo("PRIVATE");
        assertThat(key).isNotBlank();

        mockMvc.perform(get("/api/posts/{id}", postId).header("Authorization", bearer(carol)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/posts/{id}", postId)
                        .header("Authorization", bearer(carol))
                        .header("X-Post-Key", "chave-errada"))
                .andExpect(status().isNotFound());

        JsonNode byKey = read(mockMvc.perform(get("/api/posts/{id}", postId)
                        .header("Authorization", bearer(carol))
                        .header("X-Post-Key", key))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(byKey.get("title").asText()).isEqualTo("Segredo");
        assertThat(byKey.get("key").isNull()).isTrue();

        JsonNode byCoauthor = read(mockMvc.perform(get("/api/posts/{id}", postId)
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(byCoauthor.get("key").asText()).isEqualTo(key);

        assertThat(ids(mockMvc.perform(get("/api/posts").header("Authorization", bearer(carol)))
                .andExpect(status().isOk())
                .andReturn())).doesNotContain(postId);
        assertThat(ids(mockMvc.perform(get("/api/posts").header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andReturn())).contains(postId);
    }

    @Test
    void shouldNotExposePrivateRelatedPostsToOtherUsers() throws Exception {
        Account alice = account("alice");
        Account carol = account("carol");

        long privateId = createPost(alice, Map.of(
                "title", "Privado",
                "content", "Conteúdo",
                "visibility", "PRIVATE",
                "authorId", alice.id())).get("id").asLong();

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(carol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Tentando relacionar",
                                "content", "Conteúdo",
                                "authorId", carol.id(),
                                "relatedPostIds", List.of(privateId)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Post not found with id: " + privateId));

        long publicId = createPost(alice, Map.of(
                "title", "Público",
                "content", "Conteúdo",
                "authorId", alice.id(),
                "relatedPostIds", List.of(privateId))).get("id").asLong();

        JsonNode forCarol = read(mockMvc.perform(get("/api/posts/{id}", publicId)
                        .header("Authorization", bearer(carol)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(forCarol.get("relatedPosts").size()).isZero();

        JsonNode forAlice = read(mockMvc.perform(get("/api/posts/{id}", publicId)
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(forAlice.get("relatedPosts").get(0).get("id").asLong()).isEqualTo(privateId);
    }

    @Test
    void shouldKeepPostsWhenCoauthorIsDeleted() throws Exception {
        Account alice = account("alice");
        Account dave = account("dave");

        long postId = createPost(alice, Map.of(
                "title", "Coautoria",
                "content", "Conteúdo",
                "authorId", alice.id(),
                "coauthorIds", List.of(dave.id()))).get("id").asLong();

        userService.delete(dave.id());

        JsonNode found = read(mockMvc.perform(get("/api/posts/{id}", postId).header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(found.get("author").get("id").asLong()).isEqualTo(alice.id());
        assertThat(found.get("coauthors").size()).isZero();
    }

    @Test
    void shouldPassMainAuthorshipToCoauthorWhenMainAuthorIsDeleted() throws Exception {
        Account alice = account("alice");
        Account bob = account("bob");

        long sharedId = createPost(alice, Map.of(
                "title", "Compartilhado",
                "content", "Conteúdo",
                "authorId", alice.id(),
                "coauthorIds", List.of(bob.id()))).get("id").asLong();
        long soloId = createPost(alice, Map.of(
                "title", "Sozinho",
                "content", "Conteúdo",
                "authorId", alice.id())).get("id").asLong();

        userService.delete(alice.id());

        JsonNode shared = read(mockMvc.perform(get("/api/posts/{id}", sharedId).header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(shared.get("author").get("id").asLong()).isEqualTo(bob.id());
        assertThat(shared.get("coauthors").size()).isZero();

        mockMvc.perform(get("/api/posts/{id}", soloId).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidPostPayloads() throws Exception {
        Account alice = account("alice");
        Account bob = account("bob");

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", " ",
                                "content", "Conteúdo",
                                "authorId", alice.id()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Title is required"));

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Título",
                                "content", "Conteúdo"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.authorId").value("Author id is required"));

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Título",
                                "content", "Conteúdo",
                                "authorId", alice.id(),
                                "attachments", List.of(Map.of("type", "FILE", "url", "javascript:alert(1)"))))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Título",
                                "content", "Conteúdo",
                                "authorId", alice.id(),
                                "coauthorIds", List.of(-1L)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found with id: -1"));

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Título",
                                "content", "Conteúdo",
                                "authorId", bob.id()))))
                .andExpect(status().isForbidden());

        long postId = createPost(alice, Map.of(
                "title", "Post",
                "content", "Conteúdo",
                "authorId", alice.id())).get("id").asLong();

        mockMvc.perform(put("/api/posts/{id}", postId)
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Post",
                                "content", "Conteúdo",
                                "authorId", alice.id(),
                                "relatedPostIds", List.of(postId)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A post cannot be related to itself"));

        mockMvc.perform(put("/api/posts/{id}", postId)
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Post",
                                "content", "Conteúdo",
                                "authorId", alice.id(),
                                "relatedPostIds", List.of(-1L)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Post not found with id: -1"));
    }

    @Test
    void shouldNotGiveAdminSpecialAccessToPosts() throws Exception {
        Account author = account("author");
        Account admin = admin("admin");

        long postId = createPost(author, Map.of(
                "title", "Privado do autor",
                "content", "Conteúdo",
                "visibility", "PRIVATE",
                "authorId", author.id())).get("id").asLong();

        mockMvc.perform(get("/api/posts/{id}", postId).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/posts/{id}", postId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Editado pelo admin",
                                "content", "Conteúdo",
                                "authorId", admin.id()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/posts/{id}", postId).header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
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

        long id = userService.create(
                new UserRequest(username, PASSWORD, username + "@example.com", Role.ADMIN), true).id();

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

    private JsonNode acceptDeletion(Account account, long postId) throws Exception {
        return read(mockMvc.perform(put("/api/posts/{id}/deletion-acceptance", postId)
                        .header("Authorization", bearer(account)))
                .andExpect(status().isOk())
                .andReturn());
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
