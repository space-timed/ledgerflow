package com.ledgerflow;

import com.ledgerflow.common.money.CurrencyCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class LedgerFlowApplicationTests {

    @Test
    @DisplayName("Application context loads successfully with test profile")
    void contextLoads() {
        // Verifies Spring Boot context bootstraps cleanly
    }

    @Test
    @DisplayName("CurrencyCode validates ISO currencies and minor units correctly")
    void currencyValidationWorks() {
        assertTrue(CurrencyCode.isValid("INR"));
        assertTrue(CurrencyCode.isValid("USD"));
        assertTrue(CurrencyCode.isValid("EUR"));
        assertTrue(CurrencyCode.isValid("GBP"));
        assertFalse(CurrencyCode.isValid("XYZ"));
        assertFalse(CurrencyCode.isValid(null));

        CurrencyCode inr = CurrencyCode.fromString("INR");
        assertEquals(2, inr.getMinorUnitExponent());
        assertEquals("₹ 100.00", inr.formatMinorUnits(10000));

        CurrencyCode usd = CurrencyCode.fromString("USD");
        assertEquals("$ 49.99", usd.formatMinorUnits(4999));
    }
}
