package com.gkcontas.order.web;

import com.gkcontas.order.OrderService;
import com.gkcontas.order.OrderService.PlacedOrder;
import com.gkcontas.order.OrderService.UnknownProductException;
import com.gkcontas.order.catalog.OutOfStockException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
class OrderController {

    private final OrderService orders;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PlacedOrder place(@Valid @RequestBody OrderRequest request) {
        return orders.place(request.sku(), request.quantity());
    }

    @ExceptionHandler(UnknownProductException.class)
    ProblemDetail unknownProduct(UnknownProductException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "UNKNOWN_PRODUCT", e.getMessage());
    }

    @ExceptionHandler(OutOfStockException.class)
    ProblemDetail outOfStock(OutOfStockException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "OUT_OF_STOCK", e.getMessage());
        problem.setProperty("available", e.getAvailable());
        return problem;
    }

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail catalogUnavailable(ResourceAccessException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE", "Catalog service is unavailable");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse("invalid request");
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
