package com.example.avatars;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Keeps one avatar per id in UPLOAD_DIR, as <id>.png or <id>.jpg. */
@Component
public class AvatarStorage {

    private static final Logger log = LoggerFactory.getLogger(AvatarStorage.class);

    public record StoredAvatar(Path path, ImageFormat format) {
    }

    private final Path directory;

    public AvatarStorage(@Value("${avatars.upload-dir}") String directory) {
        this.directory = Path.of(directory);
    }

    public Path directory() {
        return directory;
    }

    /**
     * Creates the upload directory at startup. When that is impossible the
     * service still starts: /health then says what is wrong, which is more use
     * than a container that exits.
     */
    @PostConstruct
    void prepare() {
        log.info("storing avatars in {}", directory);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            log.warn("could not create upload directory {}: {}", directory, describe(e));
        }
        problem().ifPresent(problem ->
                log.warn("{}. /health will return 503 and uploads will fail until this is fixed.", problem));
    }

    /** Why the upload directory cannot hold avatars, or empty if it can. */
    public Optional<String> problem() {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(directory, BasicFileAttributes.class);
        } catch (NoSuchFileException e) {
            return Optional.of("upload directory " + directory + " is missing");
        } catch (IOException e) {
            return Optional.of("upload directory " + directory + " cannot be reached: " + describe(e));
        }
        if (!attributes.isDirectory()) {
            return Optional.of("upload directory " + directory + " is not a directory");
        }
        try {
            // Creating a file is the only test that catches every cause at once:
            // mode bits, ownership and read-only mounts.
            Files.delete(Files.createTempFile(directory, ".health-", ".tmp"));
        } catch (IOException e) {
            return Optional.of("upload directory " + directory + " is not writable: " + describe(e));
        }
        return Optional.empty();
    }

    /** Saves the image, replacing an earlier avatar for the same id in either format. */
    public void store(String id, ImageFormat format, byte[] data) throws IOException {
        Path target = directory.resolve(id + format.extension());
        // Write under a temporary name, then rename: a reader never sees half a file.
        Path temporary = directory.resolve("." + id + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.write(temporary, data, StandardOpenOption.CREATE_NEW);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
        for (ImageFormat other : ImageFormat.values()) {
            if (other != format) {
                Files.deleteIfExists(directory.resolve(id + other.extension()));
            }
        }
    }

    public Optional<StoredAvatar> find(String id) {
        for (ImageFormat format : ImageFormat.values()) {
            Path path = directory.resolve(id + format.extension());
            if (Files.isRegularFile(path)) {
                return Optional.of(new StoredAvatar(path, format));
            }
        }
        return Optional.empty();
    }

    /** Removes the avatar in whichever format it is stored; false if there was none. */
    public boolean delete(String id) throws IOException {
        boolean removed = false;
        for (ImageFormat format : ImageFormat.values()) {
            removed |= Files.deleteIfExists(directory.resolve(id + format.extension()));
        }
        return removed;
    }

    /** A short, lower-case reason for a failed file operation. */
    public static String describe(IOException e) {
        if (e instanceof AccessDeniedException) {
            return "permission denied (the process runs as " + processIdentity() + ")";
        }
        if (e instanceof NoSuchFileException) {
            return "no such file or directory";
        }
        if (e instanceof FileAlreadyExistsException) {
            return "a file with that name already exists";
        }
        if (e instanceof FileSystemException f && f.getReason() != null) {
            return f.getReason().toLowerCase(Locale.ROOT);
        }
        return e.toString();
    }

    private static String processIdentity() {
        try {
            var unix = new com.sun.security.auth.module.UnixSystem();
            return "uid " + unix.getUid() + ", gid " + unix.getGid();
        } catch (LinkageError | RuntimeException e) {
            // Not a Unix system, or a trimmed-down Java runtime without that module.
            return "user " + System.getProperty("user.name");
        }
    }
}
