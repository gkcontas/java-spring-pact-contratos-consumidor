package com.gkcontas.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gkcontas.shipping.QuoteService.UnknownProductException;
import com.gkcontas.shipping.catalog.CatalogClient;
import com.gkcontas.shipping.catalog.ProductWeight;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuoteServiceTest {

    private final CatalogClient catalog = mock(CatalogClient.class);
    private final QuoteService quotes = new QuoteService(catalog);

    @ParameterizedTest
    @CsvSource({
            "110, 1, 110, 16.50",
            "1000, 1, 1000, 16.50",
            "1001, 1, 1001, 21.00",
            "950, 3, 2850, 25.50"
    })
    void chargesPerStartedKilogram(int grams, int quantity, int totalGrams, String price) {
        when(catalog.findProduct("P")).thenReturn(Optional.of(new ProductWeight("P", grams)));

        QuoteService.Quote quote = quotes.quote("P", quantity);

        assertThat(quote.totalWeightGrams()).isEqualTo(totalGrams);
        assertThat(quote.price()).isEqualByComparingTo(price);
    }

    @Test
    void unknownProduct() {
        when(catalog.findProduct("XX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quotes.quote("XX", 1)).isInstanceOf(UnknownProductException.class);
    }
}
