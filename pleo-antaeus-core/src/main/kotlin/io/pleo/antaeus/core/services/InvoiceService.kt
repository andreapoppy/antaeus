/*
    Implements endpoints related to invoices.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus

class InvoiceService(
        private val dal: AntaeusDal,
        private val currencyService: CurrencyService) {

    fun fetchAll(): List<Invoice> {
       return dal.fetchInvoices()
    }

    fun fetch(id: Int): Invoice {
        return dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }

    fun fetchAllByStatus(status : InvoiceStatus) : List<Invoice> {
        return dal.fetchInvoicesByStatus(status)
    }

    fun updateStatusById(id: Int, status : InvoiceStatus) : Invoice {
        return dal.updateInvoiceStatus(id, status) ?: throw InvoiceNotFoundException(id)
    }

    fun updateCurrencyById(id: Int, fromCurrency: Currency) : Invoice? {
        val invoice = this.fetch(id)
        val customer = this.dal.fetchCustomer(invoice.customerId) ?: throw CustomerNotFoundException(invoice.customerId)
        val newAmount = currencyService.convert(invoice.amount, customer.currency)
        return this.dal.updateInvoiceAmount(invoice.id, newAmount, customer.currency)
    }
}
