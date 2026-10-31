package com.example.studentnews.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.example.studentnews.repository.NewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests: HTTP (MockMvc) through Spring MVC, service, JPA, and PostgreSQL (Testcontainers + Flyway).
 */
@SpringBootTest
@AutoConfigureMockMvc
class NewsApiE2ETest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NewsRepository newsRepository;

    @BeforeEach
    void cleanNewsItems() {
        jdbcTemplate.execute("DELETE FROM news_items");
    }

    @Test
    void postPersistsDraftInDatabaseAndReturnsCreated() throws Exception {
        long before = countRows();

        String body = mockMvc.perform(post("/api/news")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "  Trim me  ",
                                  "content": "  Body  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Trim me"))
                .andExpect(jsonPath("$.content").value("Body"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = objectMapper.readTree(body).get("id").asLong();
        assertThat(countRows()).isEqualTo(before + 1);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM news_items WHERE id = ?", String.class, id);
        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM news_items WHERE id = ?", String.class, id);
        assertThat(status).isEqualTo("DRAFT");
        assertThat(title).isEqualTo("Trim me");
        assertThat(newsRepository.existsById(id)).isTrue();
    }

    @Test
    void getListReturnsEmptyArrayWhenNoRows() throws Exception {
        assertThat(countRows()).isZero();

        mockMvc.perform(get("/api/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getListReturnsAllRowsFromDatabase() throws Exception {
        insertRow("A", "c1", "DRAFT");
        insertRow("B", "c2", "PUBLISHED");

        String raw = mockMvc.perform(get("/api/news"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode arr = objectMapper.readTree(raw);
        assertThat(arr.size()).isEqualTo(2);
        var titles = StreamSupport.stream(arr.spliterator(), false)
                .map(n -> n.get("title").asText())
                .toList();
        assertThat(titles).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void getWithStatusFilterMatchesDatabase() throws Exception {
        insertRow("Draft only", "x", "DRAFT");
        insertRow("Published only", "y", "PUBLISHED");

        String raw = mockMvc.perform(get("/api/news?status=PUBLISHED"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode arr = objectMapper.readTree(raw);
        assertThat(arr.size()).isEqualTo(1);
        assertThat(arr.get(0).get("title").asText()).isEqualTo("Published only");
        assertThat(arr.get(0).get("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test
    void putUpdatesRowInDatabase() throws Exception {
        long id = insertRow("Old", "old body", "DRAFT");

        mockMvc.perform(put("/api/news/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New","content":"new body"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New"))
                .andExpect(jsonPath("$.content").value("new body"));

        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM news_items WHERE id = ?", String.class, id);
        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM news_items WHERE id = ?", String.class, id);
        assertThat(title).isEqualTo("New");
        assertThat(content).isEqualTo("new body");
    }

    @Test
    void publishAndArchiveUpdateStatusInDatabase() throws Exception {
        long id = insertRow("T", "c", "DRAFT");

        mockMvc.perform(patch("/api/news/{id}/publish", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM news_items WHERE id = ?", String.class, id))
                .isEqualTo("PUBLISHED");

        mockMvc.perform(patch("/api/news/{id}/archive", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM news_items WHERE id = ?", String.class, id))
                .isEqualTo("ARCHIVED");
    }

    @Test
    void deleteRemovesRowFromDatabase() throws Exception {
        long id = insertRow("Del", "c", "DRAFT");
        assertThat(countRows()).isEqualTo(1);

        mockMvc.perform(delete("/api/news/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(countRows()).isZero();
        assertThat(newsRepository.existsById(id)).isFalse();
    }

    @Test
    void fullLifecycleFromHttpToDatabase() throws Exception {
        String createdRaw = mockMvc.perform(post("/api/news")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Semester","content":"Starts Monday"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(createdRaw).get("id").asLong();

        mockMvc.perform(put("/api/news/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated\",\"content\":\"Text\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/news/{id}/publish", id))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/news?status=PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(Math.toIntExact(id)));

        mockMvc.perform(patch("/api/news/{id}/archive", id))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/news/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM news_items WHERE id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void putPublishArchiveDeleteReturn404WhenIdMissing() throws Exception {
        long missingId = 9_999_999L;

        mockMvc.perform(put("/api/news/{id}", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("News with id " + missingId + " not found"));

        mockMvc.perform(patch("/api/news/{id}/publish", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(patch("/api/news/{id}/archive", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(delete("/api/news/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void postWithInvalidBodyDoesNotInsertRow() throws Exception {
        long before = countRows();

        mockMvc.perform(post("/api/news")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"\"}"))
                .andExpect(status().isBadRequest());

        assertThat(countRows()).isEqualTo(before);
    }

    private long countRows() {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM news_items", Long.class);
        return n == null ? 0 : n;
    }

    /**
     * Inserts a row matching the JPA entity/Flyway schema (timestamps required).
     */
    private long insertRow(String title, String content, String status) {
        jdbcTemplate.update(
                """
                        INSERT INTO news_items (title, content, status, created_at, updated_at)
                        VALUES (?, ?, ?, NOW(), NOW())
                        """,
                title, content, status);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM news_items", Long.class);
    }
}
