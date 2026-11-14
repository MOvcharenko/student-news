package com.example.avatars;

import org.springframework.http.HttpStatus;

/** An error the client sees as {"error": ..., "detail": ...} with the given status. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String detail) {
        super(detail);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
