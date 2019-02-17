package io.pleo.antaeus.core.services

import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Money
import java.math.BigDecimal

class CurrencyService {

    private val eurToDkk = BigDecimal(0.13)
    private val usdToDkk = BigDecimal(6.60)
    private val gbpToDkk = BigDecimal(12.73)
    private val sekToDkk = BigDecimal(1.42)

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

        val result = when(toCurrency) {
            Currency.DKK -> amountInDkk
            Currency.EUR -> amountInDkk / eurToDkk
            Currency.USD -> amountInDkk / usdToDkk
            Currency.GBP -> amountInDkk / gbpToDkk
            Currency.SEK -> amountInDkk / sekToDkk
        }

        return Money(result, toCurrency)
    }
}