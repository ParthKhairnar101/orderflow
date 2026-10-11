package com.parth.orderflow.common;

import com.parth.orderflow.product.DuplicateSkuException;
import com.parth.orderflow.product.InsufficientInventoryException;
import com.parth.orderflow.product.ProductNotFoundException;
import com.parth.orderflow.order.ConcurrentOrderUpdateException;
import com.parth.orderflow.order.DuplicateOrderProductException;
import com.parth.orderflow.order.IdempotencyKeyReuseException;
import com.parth.orderflow.order.InvalidIdempotencyKeyException;
import com.parth.orderflow.order.OrderNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(ProductNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Product not found", exception.getMessage());
    }

    @ExceptionHandler(DuplicateSkuException.class)
    ResponseEntity<ProblemDetail> handleDuplicateSku(DuplicateSkuException exception) {
        return problem(HttpStatus.CONFLICT, "Duplicate SKU", exception.getMessage());
    }

    @ExceptionHandler({InsufficientInventoryException.class, IdempotencyKeyReuseException.class,
            ConcurrentOrderUpdateException.class})
    ResponseEntity<ProblemDetail> handleOrderConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Order conflict", exception.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ProblemDetail> handleOrderNotFound(OrderNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Order not found", exception.getMessage());
    }

    @ExceptionHandler({InvalidIdempotencyKeyException.class, DuplicateOrderProductException.class})
    ResponseEntity<ProblemDetail> handleInvalidOrder(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid order", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().stream()
                .sorted((left, right) -> left.getField().compareTo(right.getField()))
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more request fields are invalid.");
        detail.setTitle("Validation failed");
        detail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", "The request body is missing or invalid.");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "Concurrent update", "The resource changed during this request. Reload it and retry.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataConflict(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT, "Data conflict", "The request conflicts with an existing database constraint.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(title);
        return ResponseEntity.status(status).body(detail);
    }
}
