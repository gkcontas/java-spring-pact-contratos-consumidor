package com.gkcontas.shipping.web;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gkcontas.shipping.QuoteService;
import com.gkcontas.shipping.QuoteService.Quote;
import com.gkcontas.shipping.QuoteService.UnknownProductException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuoteController.class)
class QuoteControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    QuoteService quotes;

    @Test
    void returnsAQuote() throws Exception {
        when(quotes.quote("MS-002", 2)).thenReturn(new Quote("MS-002", 2, 220, new BigDecimal("16.50")));

        mvc.perform(get("/api/shipping-quotes").param("sku", "MS-002").param("quantity", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWeightGrams").value(220));
    }

    @Test
    void unknownProductIs422() throws Exception {
        when(quotes.quote(anyString(), anyInt())).thenThrow(new UnknownProductException("XX"));

        mvc.perform(get("/api/shipping-quotes").param("sku", "XX").param("quantity", "1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PRODUCT"));
    }

    @Test
    void invalidQuantityIs400InEnglish() throws Exception {
        mvc.perform(get("/api/shipping-quotes").param("sku", "MS-002").param("quantity", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("quantity must be positive"));
    }
}
