package com.gkcontas.order.web;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gkcontas.order.OrderService;
import com.gkcontas.order.OrderService.PlacedOrder;
import com.gkcontas.order.OrderService.UnknownProductException;
import com.gkcontas.order.catalog.OutOfStockException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService orders;

    @Test
    void placesAnOrder() throws Exception {
        when(orders.place("KB-001", 2)).thenReturn(new PlacedOrder("ORD-1", "KB-001", "Keyboard", 2,
                new BigDecimal("10.00"), new BigDecimal("20.00"), UUID.randomUUID()));

        mvc.perform(order("{\"sku\":\"KB-001\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(20.00));
    }

    @Test
    void unknownProductIs422() throws Exception {
        when(orders.place(anyString(), anyInt())).thenThrow(new UnknownProductException("XX"));

        mvc.perform(order("{\"sku\":\"XX\",\"quantity\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PRODUCT"));
    }

    @Test
    void outOfStockIs409WithAvailableUnits() throws Exception {
        when(orders.place(anyString(), anyInt())).thenThrow(new OutOfStockException("MN-003", 1));

        mvc.perform(order("{\"sku\":\"MN-003\",\"quantity\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.available").value(1));
    }

    @Test
    void catalogDownIs503() throws Exception {
        when(orders.place(anyString(), anyInt())).thenThrow(new ResourceAccessException("Connection refused"));

        mvc.perform(order("{\"sku\":\"KB-001\",\"quantity\":1}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
    }

    @Test
    void invalidRequestIs400InEnglish() throws Exception {
        mvc.perform(order("{\"sku\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("quantity: quantity must be positive; sku: sku is required"));
    }

    private static org.springframework.test.web.servlet.RequestBuilder order(String json) {
        return post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
