package com.example.avatars;

import java.util.Arrays;
import java.util.Optional;

/**
 * The image formats the service accepts. An upload's format is decided by its
 * first bytes: the content type the client declares is whatever the client
 * chose to send, so it is ignored.
 */
public enum ImageFormat {
    PNG(".png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}),
    JPEG(".jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

    private final String extension;
    private final String contentType;
    private final byte[] magic;

    ImageFormat(String extension, String contentType, byte[] magic) {
        this.extension = extension;
        this.contentType = contentType;
        this.magic = magic;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<ImageFormat> detect(byte[] data) {
        return Arrays.stream(values())
                .filter(format -> data.length >= format.magic.length
                        && Arrays.equals(data, 0, format.magic.length, format.magic, 0, format.magic.length))
                .findFirst();
    }
}
