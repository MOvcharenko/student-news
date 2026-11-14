package com.example.avatars;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
public class AvatarController {

    static final String SEND_AS_FILE_FIELD = "send the image as the multipart form field \"file\"";

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public record Uploaded(String id, String url, long size, @JsonProperty("content_type") String contentType) {
    }

    private final AvatarStorage storage;
    private final long maxBytes;

    public AvatarController(AvatarStorage storage, @Value("${avatars.max-bytes}") long maxBytes) {
        this.storage = storage;
        this.maxBytes = maxBytes;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return storage.problem()
                .map(problem -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("status", "error", "detail", problem)))
                .orElseGet(() -> ResponseEntity.ok(Map.of("status", "ok")));
    }

    @PostMapping("/avatars/{id}")
    public ResponseEntity<Uploaded> upload(@PathVariable String id,
                                           @RequestParam(name = "file", required = false) MultipartFile file)
            throws IOException {
        checkId(id);
        if (file == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, SEND_AS_FILE_FIELD);
        }
        // Tomcat already refuses larger files; this repeats the check for any
        // other way in, such as the tests' MockMvc.
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorResponses.tooLarge(maxBytes));
        }
        byte[] data = file.getBytes();
        ImageFormat format = ImageFormat.detect(data)
                .orElseThrow(() -> new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "the file is not a PNG or JPEG image"));
        try {
            storage.store(id, format, data);
        } catch (IOException e) {
            String detail = storage.problem().orElse("writing the file failed: " + AvatarStorage.describe(e));
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "cannot store the avatar: " + detail);
        }
        String url = "/avatars/" + id;
        return ResponseEntity.created(URI.create(url))
                .body(new Uploaded(id, url, data.length, format.contentType()));
    }

    @GetMapping("/avatars/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        checkId(id);
        AvatarStorage.StoredAvatar avatar = storage.find(id).orElseThrow(() -> notFound(id));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.format().contentType()))
                .body(new FileSystemResource(avatar.path()));
    }

    @DeleteMapping("/avatars/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        checkId(id);
        boolean removed;
        try {
            removed = storage.delete(id);
        } catch (IOException e) {
            String detail = storage.problem().orElse("removing the file failed: " + AvatarStorage.describe(e));
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "cannot delete the avatar: " + detail);
        }
        if (!removed) {
            throw notFound(id);
        }
        return ResponseEntity.noContent().build();
    }

    private static void checkId(String id) {
        if (!ID.matcher(id).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "id must be 1 to 64 characters from A-Z, a-z, 0-9, _ and -");
        }
    }

    private static ApiException notFound(String id) {
        return new ApiException(HttpStatus.NOT_FOUND, "no avatar stored for id " + id);
    }
}
