package com.enterprise.procurement.exception;

import com.enterprise.procurement.dto.ApiResponse; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.util.NoSuchElementException;

@RestControllerAdvice public class GlobalExceptionHandler { @ExceptionHandler(NoSuchElementException.class) ResponseEntity<ApiResponse<Void>> notFound(Exception e){return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(e.getMessage()));} @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) ResponseEntity<ApiResponse<Void>> bad(Exception e){return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));} @ExceptionHandler(Exception.class) ResponseEntity<ApiResponse<Void>> generic(Exception e){return ResponseEntity.status(500).body(ApiResponse.fail("服务器内部错误"));} }
