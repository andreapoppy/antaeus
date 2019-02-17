package io.pleo.antaeus.core.services

import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Money
import java.math.BigDecimal

/*
 CurrencyService: provides a simple way to convert amounts of invoices, it should be a reliable external service or
 a company-reviewed library. It is important to handle the correctly the rounding of the conversions.
 The hard-coded values are taken from https://transferwise.com/gb/currency-converter on 17th of February 2019
 */

class CurrencyService {

    private val eurToDkk = BigDecimal(7.4584)
    private val usdToDkk = BigDecimal(6.60292)
    private val gbpToDkk = BigDecimal(8.51526)
    private val sekToDkk = BigDecimal(0.71213)

    // Converts an amount to a target currency
    fun convert(amount: Money, toCurrency : Currency) : Money {

        if (amount.currency == toCurrency) {
            return amount
        }

        val amountInDkk = when(amount.currency) {
            Currency.DKK -> amount.value
            Currency.EUR -> amount.value * eurToDkk
            Currency.USD -> amount.value * usdToDkk
            Currency.GBP -> amount.value * gbpToDkk
            Currency.SEK -> amount.value * sekToDkk
        }

        var result = when(toCurrency) {
            Currency.DKK -> amountInDkk
            Currency.EUR -> amountInDkk / eurToDkk
            Currency.USD -> amountInDkk / usdToDkk
            Currency.GBP -> amountInDkk / gbpToDkk
            Currency.SEK -> amountInDkk / sekToDkk
        }

        result = result.setScale(2, BigDecimal.ROUND_HALF_UP);

        return Money(result, toCurrency)
    }
}