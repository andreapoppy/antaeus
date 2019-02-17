package io.pleo.antaeus.core.services

import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigDecimal.ROUND_HALF_UP

/*
 Basic test suite for the _CurrencyService_ class
 */

class CurrencyServiceTests {

    private val roundingMode = ROUND_HALF_UP

    @Test
    fun `CurrencyService can convert EUR to DKK`() {

        // Arrange
        var currencyService = CurrencyService()
        var expected = BigDecimal(313.25280)
        expected = expected.setScale(2, roundingMode);

        // Act
        var actual = currencyService.convert(Money(BigDecimal(42), Currency.EUR), Currency.DKK)

        // Assert
        assert(actual.currency == Currency.DKK)
        assert(actual.value == expected)
    }

    @Test
    fun `CurrencyService can convert DKK to EUR`() {

        // Arrange
        var currencyService = CurrencyService()
        var expected = BigDecimal(6)
        expected = expected.setScale(2, roundingMode);

        // Act
        var actual = currencyService.convert(Money(BigDecimal(42), Currency.DKK), Currency.EUR)

        // Assert
        assert(actual.currency == Currency.EUR)
        assert(actual.value == expected)
    }

    @Test
    fun `CurrencyService can convert EUR to GDP`() {

        // Arrange
        var currencyService = CurrencyService()
        var expected = BigDecimal(36.7878)
        expected = expected.setScale(2, roundingMode);

        // Act
        var actual = currencyService.convert(Money(BigDecimal(42), Currency.EUR), Currency.GBP)

        // Assert
        assert(actual.currency == Currency.GBP)
        assert(actual.value == expected)
    }
}