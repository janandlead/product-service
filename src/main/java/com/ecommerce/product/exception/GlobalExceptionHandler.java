package com.ecommerce.product.exception;

import com.ecommerce.product.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ProductNotFoundException.class)
	ResponseEntity<ErrorResponse> missing(ProductNotFoundException e, HttpServletRequest r) {
		return error(404, "PRODUCT_NOT_FOUND", e.getMessage(), r);
	}

	@ExceptionHandler(DuplicateSkuException.class)
	ResponseEntity<ErrorResponse> duplicate(DuplicateSkuException e, HttpServletRequest r) {
		return error(409, "DUPLICATE_SKU", e.getMessage(), r);
	}

	@ExceptionHandler(InsufficientStockException.class)
	ResponseEntity<ErrorResponse> stock(InsufficientStockException e, HttpServletRequest r) {
		log.warn("Insufficient stock: {}", e.getMessage());
		return ResponseEntity.status(409)
				.body(new ErrorResponse(Instant.now(), 409, "INSUFFICIENT_STOCK", e.getMessage(), r.getRequestURI(),
						MDC.get("correlationId"), e.getAvailableQuantity(), e.getRequestedQuantity(), null));
	}

	@ExceptionHandler(InvalidInventoryOperationException.class)
	ResponseEntity<ErrorResponse> invalidOperation(InvalidInventoryOperationException e, HttpServletRequest r) {
		return error(409, "INVALID_INVENTORY_OPERATION", e.getMessage(), r);
	}

	@ExceptionHandler({ OptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class })
	ResponseEntity<ErrorResponse> concurrent(Exception e, HttpServletRequest r) {
		log.warn("Optimistic locking conflict path={}", r.getRequestURI());
		return error(409, "CONCURRENT_INVENTORY_CONFLICT",
				"Concurrent update; retry the entire request with the same order ID and items", r);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException e, HttpServletRequest r) {
		Throwable cause = e;
		while (cause.getCause() != null) {
			if (cause instanceof org.hibernate.exception.ConstraintViolationException c
					&& "uk_product_sku".equals(c.getConstraintName()))
				return error(409, "DUPLICATE_SKU", "SKU already exists", r);
			cause = cause.getCause();
		}
		return error(409, "DATA_CONFLICT", "Operation conflicts with existing data or database constraints", r);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
		Map<String, String> fields = new TreeMap<>();
		e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
		return ResponseEntity.badRequest().body(new ErrorResponse(Instant.now(), 400, "VALIDATION_ERROR",
				"Request validation failed", r.getRequestURI(), MDC.get("correlationId"), null, null, fields));
	}

	@ExceptionHandler({ IllegalArgumentException.class, ConstraintViolationException.class,
			HandlerMethodValidationException.class, HttpMessageNotReadableException.class,
			MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class })
	ResponseEntity<ErrorResponse> badRequest(Exception e, HttpServletRequest r) {
		return error(400, "INVALID_REQUEST", "Invalid request body, path or query parameters", r);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorResponse> notFound(Exception e, HttpServletRequest r) {
		return error(404, "NOT_FOUND", "Endpoint not found", r);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ErrorResponse> method(Exception e, HttpServletRequest r) {
		return error(405, "METHOD_NOT_ALLOWED", "HTTP method not supported", r);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ErrorResponse> media(Exception e, HttpServletRequest r) {
		return error(415, "UNSUPPORTED_MEDIA_TYPE", "Use application/json", r);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> unexpected(Exception e, HttpServletRequest r) {
		log.error("Unexpected failure path={}", r.getRequestURI(), e);
		return error(500, "INTERNAL_ERROR", "An unexpected error occurred", r);
	}

	private ResponseEntity<ErrorResponse> error(int status, String code, String message, HttpServletRequest r) {
		return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status, code, message,
				r.getRequestURI(), MDC.get("correlationId"), null, null, null));
	}
}
