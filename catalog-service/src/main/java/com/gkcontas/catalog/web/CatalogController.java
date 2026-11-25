package com.gkcontas.catalog.web;

import com.gkcontas.catalog.product.ProductService;
import com.gkcontas.catalog.reservation.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class CatalogController {

    private final ProductService products;
    private final ReservationService reservations;

    @GetMapping("/products/{sku}")
    ProductResponse product(@PathVariable String sku) {
        return ProductResponse.of(products.find(sku));
    }

    @PostMapping("/stock-reservations")
    @ResponseStatus(HttpStatus.CREATED)
    ReservationResponse reserve(@Valid @RequestBody ReservationRequest request) {
        return ReservationResponse.of(reservations.reserve(request.sku(), request.quantity(), request.orderRef()));
    }
}
