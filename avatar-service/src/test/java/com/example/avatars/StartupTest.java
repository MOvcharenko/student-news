package com.example.avatars;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.web.context.WebApplicationContext;

import java.net.ServerSocket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Configuration, and what the service does when UPLOAD_DIR is missing or unwritable. */
@ExtendWith(OutputCaptureExtension.class)
class StartupTest {

    @TempDir
    Path tmp;

    static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(AvatarServiceApplication.class).run(args);
    }

    static ConfigurableApplicationContext startWithUploadDir(Path uploadDir) {
        return start("--server.port=0", "--avatars.upload-dir=" + uploadDir);
    }

    static MockMvc mvc(ConfigurableApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
    }

    static MockMultipartFile png() {
        return new MockMultipartFile("file", AvatarApiTest.PNG);
    }

    // ----------------------------------------------------- configuration

    @Test
    void defaultsApplyWhenNoEnvironmentVariableIsSet() throws Exception {
        Properties properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        var placeholders = new PropertyPlaceholderHelper("${", "}", ":", null, false);
        PropertyPlaceholderHelper.PlaceholderResolver nothingSet = name -> null;

        assertThat(placeholders.replacePlaceholders(properties.getProperty("server.port"), nothingSet))
                .isEqualTo("8090");
        assertThat(placeholders.replacePlaceholders(properties.getProperty("avatars.upload-dir"), nothingSet))
                .isEqualTo("/data/avatars");
        assertThat(placeholders.replacePlaceholders(properties.getProperty("avatars.max-bytes"), nothingSet))
                .isEqualTo("2097152");
    }

    @Test
    void readsPortUploadDirAndMaxBytes() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path uploadDir = tmp.resolve("from-env");
        // Command-line arguments fill the same ${PORT}, ${UPLOAD_DIR} and
        // ${MAX_BYTES} placeholders as environment variables do.
        try (var context = start("--PORT=" + port, "--UPLOAD_DIR=" + uploadDir, "--MAX_BYTES=4096")) {
            assertThat(context.getEnvironment().getProperty("local.server.port", Integer.class)).isEqualTo(port);
            assertThat(context.getBean(AvatarStorage.class).directory()).isEqualTo(uploadDir);
            assertThat(context.getEnvironment().getProperty("avatars.max-bytes", Long.class)).isEqualTo(4096L);
            assertThat(uploadDir).isDirectory();
        }
    }

    // -------------------------------------------------------- upload dir

    @Test
    void startsEvenWhenTheUploadDirCannotBeCreated(CapturedOutput output) throws Exception {
        // Nothing can be created below a regular file, not even by root.
        Path blocker = Files.createFile(tmp.resolve("a-file"));
        Path uploadDir = blocker.resolve("avatars");

        try (var context = startWithUploadDir(uploadDir)) {
            MockMvc mvc = mvc(context);
            mvc.perform(get("/health"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.detail").value(containsString(uploadDir.toString())));
            mvc.perform(multipart("/avatars/news-1").file(png()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value(allOf(
                            containsString("cannot store the avatar"), containsString(uploadDir.toString()))));
        }
        assertThat(output.getOut()).contains("could not create upload directory " + uploadDir);
    }

    @Test
    void healthReportsAMissingUploadDir() throws Exception {
        Path uploadDir = tmp.resolve("avatars");
        try (var context = startWithUploadDir(uploadDir)) {
            Files.delete(uploadDir);
            mvc(context).perform(get("/health"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.detail").value("upload directory " + uploadDir + " is missing"));
        }
    }

    Path readOnlyDir() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "needs POSIX file permissions");
        Path dir = Files.createDirectory(tmp.resolve("read-only"));
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-xr-xr-x"));
        assumeFalse(Files.isWritable(dir), "running as root, which can write to any directory");
        return dir;
    }

    @Test
    void healthReportsAnUnwritableUploadDir() throws Exception {
        Path uploadDir = readOnlyDir();
        try (var context = startWithUploadDir(uploadDir)) {
            mvc(context).perform(get("/health"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.detail").value(allOf(
                            containsString(uploadDir.toString()), containsString("permission denied"))));
        }
    }

    @Test
    void uploadToAnUnwritableUploadDirReturns500() throws Exception {
        Path uploadDir = readOnlyDir();
        try (var context = startWithUploadDir(uploadDir)) {
            mvc(context).perform(multipart("/avatars/news-1").file(png()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.detail").value(allOf(containsString("cannot store the avatar"),
                            containsString(uploadDir.toString()), containsString("permission denied"))));
        }
        try (var files = Files.list(uploadDir)) {
            assertThat(files).isEmpty();
        }
    }
}
