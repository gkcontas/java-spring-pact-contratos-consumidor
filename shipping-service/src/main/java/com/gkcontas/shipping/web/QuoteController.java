package com.gkcontas.shipping.web;

import com.gkcontas.shipping.QuoteService;
import com.gkcontas.shipping.QuoteService.Quote;
import com.gkcontas.shipping.QuoteService.UnknownProductException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestController
@RequestMapping("/api/shipping-quotes")
@RequiredArgsConstructor
class QuoteController {

    private final QuoteService quotes;

    @GetMapping
    Quote quote(@RequestParam @NotBlank(message = "sku is required") String sku,
                @RequestParam @Positive(message = "quantity must be positive")
                @Max(value = 1000, message = "quantity must be at most 1000") int quantity) {
        return quotes.quote(sku, quantity);
    }

    @ExceptionHandler(UnknownProductException.class)
    ProblemDetail unknownProduct(UnknownProductException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setProperty("code", "UNKNOWN_PRODUCT");
        return problem;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail invalid(HandlerMethodValidationException e) {
        String detail = e.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse("invalid request");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("code", "VALIDATION_FAILED");
        return problem;
    }
}
