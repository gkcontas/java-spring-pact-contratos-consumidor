package com.gkcontas.catalog.web;

import com.gkcontas.catalog.reservation.ReservationService.Reserved;
import java.util.UUID;

public record ReservationResponse(UUID reservationId, String sku, int quantity, int remainingStock) {

    static ReservationResponse of(Reserved reserved) {
        return new ReservationResponse(reserved.reservation().getId(), reserved.reservation().getSku(),
                reserved.reservation().getQuantity(), reserved.remainingStock());
    }
}
