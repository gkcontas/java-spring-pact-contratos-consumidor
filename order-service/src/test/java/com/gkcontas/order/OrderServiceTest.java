package com.gkcontas.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gkcontas.order.OrderService.PlacedOrder;
import com.gkcontas.order.OrderService.UnknownProductException;
import com.gkcontas.order.catalog.CatalogClient;
import com.gkcontas.order.catalog.ProductView;
import com.gkcontas.order.catalog.ReservationView;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Business rules belong here, not in the contract: the pact says the catalog returns
 * a decimal price, this test says what the order does with it.
 */
class OrderServiceTest {

    private final CatalogClient catalog = mock(CatalogClient.class);
    private final OrderService orders = new OrderService(catalog);

    @Test
    void totalIsUnitPriceTimesQuantity() {
        UUID reservationId = UUID.randomUUID();
        when(catalog.findProduct("KB-001"))
                .thenReturn(Optional.of(new ProductView("KB-001", "Keyboard", new BigDecimal("349.90"))));
        when(catalog.reserve(eq("KB-001"), eq(3), anyString())).thenReturn(new ReservationView(reservationId));

        PlacedOrder order = orders.place("KB-001", 3);

        assertThat(order.total()).isEqualByComparingTo("1049.70");
        assertThat(order.reservationId()).isEqualTo(reservationId);
        assertThat(order.orderRef()).matches("ORD-[0-9a-f-]{36}");
    }

    @Test
    void unknownProductIsNeverReserved() {
        when(catalog.findProduct("XX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orders.place("XX", 1)).isInstanceOf(UnknownProductException.class);
        verify(catalog, never()).reserve(anyString(), eq(1), anyString());
    }
}
