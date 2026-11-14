package com.example.avatars;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.FileSystemUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every endpoint and error case, against a running application with a 1 KiB limit. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "avatars.max-bytes=1024")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AvatarApiTest {

    // The service only looks at the first bytes, so these stand in for images.
    static final byte[] PNG = withRest(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
    static final byte[] JPEG = withRest(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0});
    static final byte[] GIF = withRest("GIF89a".getBytes(StandardCharsets.US_ASCII));

    static final int MAX_BYTES = 1024;

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) {
        registry.add("avatars.upload-dir", () -> uploadDir.toString());
    }

    @Autowired
    MockMvc mvc;

    @Value("${local.server.port}")
    int port;

    @BeforeEach
    void emptyUploadDir() throws IOException {
        FileSystemUtils.deleteRecursively(uploadDir);
        Files.createDirectories(uploadDir);
    }

    static byte[] withRest(byte[] magic) {
        byte[] data = Arrays.copyOf(magic, magic.length + 16);
        Arrays.fill(data, magic.length, data.length, (byte) 'x');
        return data;
    }

    static byte[] padded(byte[] magic, int size) {
        byte[] data = Arrays.copyOf(magic, size);
        Arrays.fill(data, magic.length, size, (byte) 0);
        return data;
    }

    ResultActions upload(String id, byte[] data) throws Exception {
        return upload(id, data, MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    ResultActions upload(String id, byte[] data, String declaredType) throws Exception {
        return mvc.perform(multipart("/avatars/{id}", id).file(new MockMultipartFile("file", "avatar", declaredType, data)));
    }

    String[] storedFiles() throws IOException {
        try (Stream<Path> files = Files.list(uploadDir)) {
            return files.map(path -> path.getFileName().toString()).sorted().toArray(String[]::new);
        }
    }

    // ------------------------------------------------------------- health

    @Test
    void healthIsOkWhenTheUploadDirIsWritable() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\": \"ok\"}", true));
    }

    // ------------------------------------------------------------- upload

    @Test
    void uploadsAPng() throws Exception {
        upload("news-1", PNG)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/avatars/news-1"))
                .andExpect(content().json("""
                        {"id": "news-1", "url": "/avatars/news-1", "size": %d, "content_type": "image/png"}
                        """.formatted(PNG.length), true));
        assertThat(Files.readAllBytes(uploadDir.resolve("news-1.png"))).isEqualTo(PNG);
    }

    @Test
    void uploadsAJpeg() throws Exception {
        upload("news-2", JPEG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content_type").value("image/jpeg"));
        assertThat(Files.readAllBytes(uploadDir.resolve("news-2.jpg"))).isEqualTo(JPEG);
    }

    static Stream<Arguments> formatChanges() {
        return Stream.of(
                Arguments.of(PNG, JPEG, "news-3.jpg"),
                Arguments.of(JPEG, PNG, "news-3.png"));
    }

    @ParameterizedTest
    @MethodSource("formatChanges")
    void anUploadReplacesTheEarlierAvatarInEitherFormat(byte[] first, byte[] second, String kept) throws Exception {
        upload("news-3", first).andExpect(status().isCreated());
        upload("news-3", second).andExpect(status().isCreated());
        assertThat(storedFiles()).containsExactly(kept);
        mvc.perform(get("/avatars/news-3")).andExpect(content().bytes(second));
    }

    @ParameterizedTest
    @ValueSource(strings = {"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "A-z_0", "-", "_"})
    void acceptsValidIds(String id) throws Exception {
        upload(id, PNG).andExpect(status().isCreated());
    }

    static Stream<Arguments> invalidIdRequests() {
        String[] ids = {
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // 65 characters
                "bad.id", "bad%20id", "%C3%A9"};
        return Stream.of(HttpMethod.POST, HttpMethod.GET, HttpMethod.DELETE)
                .flatMap(method -> Arrays.stream(ids).map(id -> Arguments.of(method, id)));
    }

    @ParameterizedTest
    @MethodSource("invalidIdRequests")
    void rejectsInvalidIds(HttpMethod method, String id) throws Exception {
        URI uri = URI.create("/avatars/" + id);
        var request = method == HttpMethod.POST
                ? multipart(uri).file(new MockMultipartFile("file", PNG))
                : request(method, uri);
        mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1 to 64 characters")));
    }

    @Test
    void rejectsAnUploadWithoutTheFileField() throws Exception {
        mvc.perform(multipart("/avatars/news-1").file(new MockMultipartFile("picture", PNG)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(AvatarController.SEND_AS_FILE_FIELD));
    }

    @Test
    void rejectsARawBodyInsteadOfMultipart() throws Exception {
        mvc.perform(post("/avatars/news-1").contentType(MediaType.IMAGE_PNG).content(PNG))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(AvatarController.SEND_AS_FILE_FIELD));
    }

    static Stream<byte[]> notImages() {
        return Stream.of(GIF, "plain text".getBytes(StandardCharsets.US_ASCII), new byte[0]);
    }

    @ParameterizedTest
    @MethodSource("notImages")
    void rejectsAnythingButPngAndJpeg(byte[] data) throws Exception {
        upload("news-1", data)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"));
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void ignoresTheDeclaredContentType() throws Exception {
        upload("news-1", GIF, MediaType.IMAGE_PNG_VALUE).andExpect(status().isUnsupportedMediaType());
        upload("news-1", PNG, MediaType.TEXT_PLAIN_VALUE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content_type").value("image/png"));
    }

    @Test
    void acceptsAFileOfExactlyMaxBytes() throws Exception {
        upload("news-1", padded(PNG, MAX_BYTES))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.size").value(MAX_BYTES));
    }

    @Test
    void rejectsAFileOverMaxBytes() throws Exception {
        upload("news-1", padded(PNG, MAX_BYTES + 1))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value(ErrorResponses.tooLarge(MAX_BYTES)));
        assertThat(storedFiles()).isEmpty();
    }

    /** Over real HTTP Tomcat refuses the file before the controller runs; the answer must still be JSON. */
    @ParameterizedTest
    @ValueSource(ints = {MAX_BYTES + 1, 200 * 1024})
    void tomcatRefusesAFileOverMaxBytesWithJson(int size) throws Exception {
        HttpResponse<String> response = postOverHttp("news-1", padded(PNG, size));
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/json"));
        assertThat(response.body()).contains(ErrorResponses.tooLarge(MAX_BYTES));
        // Without this header the browser hides the answer from the news UI.
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:5173");
        assertThat(storedFiles()).isEmpty();
    }

    HttpResponse<String> postOverHttp(String id, byte[] data) throws Exception {
        String boundary = "avatar-test-boundary";
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"avatar\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(data);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/avatars/" + id))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Origin", "http://localhost:5173")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    // ---------------------------------------------------------- download

    static Stream<Arguments> storedImages() {
        return Stream.of(Arguments.of(PNG, "image/png"), Arguments.of(JPEG, "image/jpeg"));
    }

    @ParameterizedTest
    @MethodSource("storedImages")
    void returnsTheStoredImageWithItsContentType(byte[] data, String contentType) throws Exception {
        upload("news-1", data);
        mvc.perform(get("/avatars/news-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(content().bytes(data));
    }

    @Test
    void returns404JsonForAnUnknownAvatar() throws Exception {
        mvc.perform(get("/avatars/news-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("no avatar stored for id news-404"));
    }

    // ------------------------------------------------------------ delete

    @Test
    void deletesAnAvatar() throws Exception {
        upload("news-1", PNG);
        mvc.perform(delete("/avatars/news-1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(get("/avatars/news-1")).andExpect(status().isNotFound());
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void deleting404sForAnUnknownAvatar() throws Exception {
        mvc.perform(delete("/avatars/news-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // ------------------------------------------------------ other errors

    @Test
    void anUnknownPathIsJson() throws Exception {
        mvc.perform(get("/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void anUnsupportedMethodIsJson() throws Exception {
        mvc.perform(put("/avatars/news-1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("DELETE")))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    // ------------------------------------------------------- the news UI

    @Test
    void allowsTheNewsUiToCallFromTheBrowser() throws Exception {
        mvc.perform(request(HttpMethod.OPTIONS, "/avatars/news-1")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(get("/health").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    // ----------------------------------------------------------- logging

    @Test
    void logsOneLinePerRequestToStdout(CapturedOutput output) throws Exception {
        upload("news-1", PNG);
        mvc.perform(get("/avatars/news-404"));
        mvc.perform(put("/avatars/news-1"));

        assertThat(output.getOut().lines().filter(line -> line.contains("\"POST /avatars/news-1\" 201"))).hasSize(1);
        assertThat(output.getOut().lines().filter(line -> line.contains("\"GET /avatars/news-404\" 404"))).hasSize(1);
        assertThat(output.getOut().lines().filter(line -> line.contains("\"PUT /avatars/news-1\" 405"))).hasSize(1);
        // Nothing else: no second line from Spring for the 405.
        assertThat(output.getOut()).doesNotContain("not supported");
        assertThat(output.getErr()).doesNotContain("/avatars/");
    }
}
