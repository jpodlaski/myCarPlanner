package com.sanproject.service_planner.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceVatTest {

    private static final BigDecimal RATE = new BigDecimal("23");

    @Test
    void splitsGrossIntoNetAndVat() {
        var vat = InvoicePdfService.VatBreakdown.fromGross(new BigDecimal("1230.00"), RATE);

        assertThat(vat.net()).isEqualByComparingTo("1000.00");
        assertThat(vat.tax()).isEqualByComparingTo("230.00");
    }

    @Test
    void netPlusVatAlwaysEqualsGrossAfterRounding() {
        for (String amount : new String[]{"375.50", "1375.50", "0.01", "99.99", "1"}) {
            var vat = InvoicePdfService.VatBreakdown.fromGross(new BigDecimal(amount), RATE);

            assertThat(vat.net().add(vat.tax())).isEqualByComparingTo(amount);
            assertThat(vat.net().scale()).isEqualTo(2);
        }
    }
}
