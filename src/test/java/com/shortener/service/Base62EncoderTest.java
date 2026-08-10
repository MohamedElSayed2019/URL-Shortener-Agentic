package com.hcb.sc.load.shortener.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Base62EncoderTest {

    @Test
    void encodesZeroToBaseAlphabetFirstChar() {
        assertEquals("0", Base62Encoder.encode(0));
    }

    @Test
    void roundTripsVariousValues() {
        long[] values = {1, 61, 62, 1000, 123456789L, Long.MAX_VALUE / 2};
        for (long v : values) {
            String encoded = Base62Encoder.encode(v);
            long decoded = Base62Encoder.decode(encoded);
            assertEquals(v, decoded, "round trip failed for " + v);
        }
    }

    @Test
    void rejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.encode(-1));
    }

    @Test
    void rejectsInvalidCharacterOnDecode() {
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.decode("!!!"));
    }

    @Test
    void encodedValuesAreShorterThanDecimalForLargeNumbers() {
        String encoded = Base62Encoder.encode(1_000_000_000L);
        assertTrue(encoded.length() < String.valueOf(1_000_000_000L).length());
    }
}
