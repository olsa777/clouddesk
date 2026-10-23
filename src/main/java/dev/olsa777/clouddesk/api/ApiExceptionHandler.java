package dev.olsa777.clouddesk.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import dev.olsa777.clouddesk.request.InvalidTransitionException;
import dev.olsa777.clouddesk.request.RequestNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "Проверьте поля заявки", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (exception.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            var field = mapping.getPath().get(0).getFieldName();
            if (field != null) fields.put(field, "Недопустимое значение или тип поля");
        }
        return response(HttpStatus.BAD_REQUEST, "Нужен корректный JSON с допустимыми значениями полей", fields);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> parameter(MethodArgumentTypeMismatchException exception) {
        return response(HttpStatus.BAD_REQUEST, "Некорректный параметр запроса",
                Map.of(exception.getName(), "Недопустимое значение"));
    }

    @ExceptionHandler(RequestNotFoundException.class)
    ResponseEntity<ApiError> notFound(RequestNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> routeNotFound() {
        return response(HttpStatus.NOT_FOUND, "Адрес не найден", Map.of());
    }

    @ExceptionHandler(InvalidTransitionException.class)
    ResponseEntity<ApiError> conflict(InvalidTransitionException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> concurrentChange() {
        return response(HttpStatus.CONFLICT, "Заявка уже изменена. Обновите список и повторите действие", Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed() {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "HTTP-метод не поддерживается", Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> contentType() {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Используйте Content-Type: application/json", Map.of());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, fields));
    }
}
