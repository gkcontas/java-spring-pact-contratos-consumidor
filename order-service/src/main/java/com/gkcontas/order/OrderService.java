package com.gkcontas.order;

import com.gkcontas.order.catalog.CatalogClient;
import com.gkcontas.order.catalog.ProductView;
import com.gkcontas.order.catalog.ReservationView;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final CatalogClient catalog;

    public record PlacedOrder(String orderRef, String sku, String productName, int quantity,
                              BigDecimal unitPrice, BigDecimal total, UUID reservationId) {
    }

    public static class UnknownProductException extends RuntimeException {
        public UnknownProductException(String sku) {
            super("Product " + sku + " does not exist");
        }
    }

    public PlacedOrder place(String sku, int quantity) {
        ProductView product = catalog.findProduct(sku).orElseThrow(() -> new UnknownProductException(sku));
        String orderRef = "ORD-" + UUID.randomUUID();
        ReservationView reservation = catalog.reserve(sku, quantity, orderRef);
        return new PlacedOrder(orderRef, product.sku(), product.name(), quantity, product.price(),
                product.price().multiply(BigDecimal.valueOf(quantity)), reservation.reservationId());
    }
}
