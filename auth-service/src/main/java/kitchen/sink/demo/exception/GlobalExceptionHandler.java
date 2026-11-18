package kitchen.sink.demo.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import jakarta.validation.ConstraintViolationException;
import kitchen.sink.demo.dto.response.ErrorResponse;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Returns 401 when authentication fails. */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailed(AuthenticationFailedException ex) {
        return refuse(HttpStatus.UNAUTHORIZED, ex);
    }

    /** Returns 404 when no member has the requested email. */
    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMemberNotFound(MemberNotFoundException ex) {
        return refuse(HttpStatus.NOT_FOUND, ex);
    }

    /** Returns 403 when the caller is not allowed to do this. */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        return refuse(HttpStatus.FORBIDDEN, ex);
    }

    /** Returns 400 for a rejected password, grant or authorization request. */
    @ExceptionHandler({InvalidPasswordException.class, InvalidGrantException.class,
            InvalidAuthorizationRequestException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(RuntimeException ex) {
        return refuse(HttpStatus.BAD_REQUEST, ex);
    }

    private ResponseEntity<ErrorResponse> refuse(HttpStatus status, RuntimeException ex) {
        log.warn("request refused with {}: {}", status.value(), ex.getMessage());
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), ex.getMessage(), LocalDateTime.now()));
    }

    /** Returns 409 when a record with the same unique key already exists. */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKeyException(DuplicateKeyException e) {
        // the driver message quotes the duplicated value (an email), so it is not logged
        log.warn("duplicate key: a record with the same unique value already exists");
        ErrorResponse body = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Member Already Exist",
                LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }


    /** Returns 400 for a failed parameter validation. */
    @ExceptionHandler(exception = {ConstraintViolationException.class })
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("constraint violation: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                LocalDateTime.now());
        return ResponseEntity.badRequest().body(body);
    }

    // Spring MVC's own client-error exceptions; without these the catch-all below turns them into 500s.
    /** Returns 400 with the messages of the failed method validations. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(java.util.stream.Collectors.joining(", "));
        log.warn("invalid request parameters: {}", message);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, message, LocalDateTime.now()));
    }

    /** Returns 400 for unreadable, mistyped or incomplete requests. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        log.warn("bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "Malformed request", LocalDateTime.now()));
    }

    /** Returns 500 with a generic message for any unhandled error. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> fallBackException(Exception e) {
        log.error("uncaught exception", e);
        ErrorResponse body = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Something went wrong, please try again later",
                LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
