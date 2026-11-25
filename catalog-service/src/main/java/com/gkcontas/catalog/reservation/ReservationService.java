package com.gkcontas.catalog.reservation;

import com.gkcontas.catalog.product.Product;
import com.gkcontas.catalog.product.ProductNotFoundException;
import com.gkcontas.catalog.product.ProductRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ProductRepository products;
    private final StockReservationRepository reservations;
    private final Clock clock;

    public record Reserved(StockReservation reservation, int remainingStock) {
    }

    @Transactional
    public Reserved reserve(String sku, int quantity, String orderRef) {
        if (products.decrementStock(sku, quantity) == 0) {
            Product product = products.findById(sku).orElseThrow(() -> new ProductNotFoundException(sku));
            throw new InsufficientStockException(sku, quantity, product.getStock());
        }
        StockReservation reservation = reservations.save(StockReservation.builder()
                .id(UUID.randomUUID())
                .sku(sku)
                .quantity(quantity)
                .orderRef(orderRef)
                .createdAt(Instant.now(clock))
                .build());
        int remaining = products.findById(sku).orElseThrow().getStock();
        return new Reserved(reservation, remaining);
    }
}
