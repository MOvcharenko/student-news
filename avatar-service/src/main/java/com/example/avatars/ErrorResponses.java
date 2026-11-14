package com.example.avatars;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the service as {"error": "Not Found", "detail": "..."},
 * including the ones Spring raises itself (404 for an unknown path, 405, and
 * 413 when Tomcat refuses an upload over MAX_BYTES).
 */
@RestControllerAdvice
public class ErrorResponses extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorResponses.class);

    public record ErrorBody(String error, String detail) {
    }

    private final long maxBytes;

    public ErrorResponses(@Value("${avatars.max-bytes}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public static String tooLarge(long maxBytes) {
        return "the file is larger than MAX_BYTES (" + maxBytes + " bytes)";
    }

    static ResponseEntity<Object> body(HttpStatusCode status, HttpHeaders headers, String detail) {
        String error = HttpStatus.valueOf(status.value()).getReasonPhrase();
        return new ResponseEntity<>(new ErrorBody(error, detail), headers, status);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException e) {
        return body(e.status(), HttpHeaders.EMPTY, e.getMessage());
    }

    /** A request that is not multipart/form-data at all. */
    @ExceptionHandler(MultipartException.class)
    ResponseEntity<Object> handleMultipartException(MultipartException e) {
        return body(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY, AvatarController.SEND_AS_FILE_FIELD);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception e) {
        log.error("unexpected error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY, "unexpected error: " + e);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object defaultBody, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        String detail;
        if (e instanceof MaxUploadSizeExceededException) {
            detail = tooLarge(maxBytes);
        } else if (e instanceof ErrorResponse response && response.getBody().getDetail() != null) {
            detail = response.getBody().getDetail();
        } else {
            detail = e.getMessage();
        }
        return body(status, headers, detail);
    }
}
