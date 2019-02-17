package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InvoiceServiceTest {
    private val dal = mockk<AntaeusDal> {
        every { fetchInvoice(404) } returns null
    }

    private val currencyService = mockk<CurrencyService> {

    }

    private val invoiceService = InvoiceService(dal = dal, currencyService = currencyService)

    @Test
    fun `will throw if customer is not found`() {
        assertThrows<InvoiceNotFoundException> {
            invoiceService.fetch(404)
        }
    }
}