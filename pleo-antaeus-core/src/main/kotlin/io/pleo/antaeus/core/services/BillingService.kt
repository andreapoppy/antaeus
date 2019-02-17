package io.pleo.antaeus.core.services

import com.kizitonwose.time.minutes
import io.pleo.antaeus.core.contracts.IDateTimeProvider
import io.pleo.antaeus.core.contracts.ILogger
import io.pleo.antaeus.core.contracts.ITimeOutProvider
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*
import java.time.Duration

/*
    BillingService handles the processing of invoices. The service can run in the background on its own
    thread as a coroutine. This service depends on:
     - The invoice service
     - The customer service
     - An IDateTimeProvider in order to control time-related logic
     - An ITimeOutProvider in order to control how the billing service goes in idle mode
     - A Payment provider for the actual processing of the invoices (external)
     - A ILogger implementation for providing basic logging
 */
open class BillingService(
        private val invoiceService: InvoiceService,
        private val customerService: CustomerService,
        private val dateTimeProvider: IDateTimeProvider,
        private val timeOutProvider: ITimeOutProvider,
        private val paymentProvider: PaymentProvider,
        private val logger : ILogger) {

    private var job : Job? = null
    private var cancellationToken = false
    protected var iterationsCount : Int = 0
    protected open val iterationSleepTime : Long = 5.minutes.inSeconds.longValue
    protected open val networkOutageSleepTime : Long = 1.minutes.inSeconds.longValue

    // Runs the billing service as a coroutine
    fun run() {
        logger.info("BillingService running")
        job = GlobalScope.launch {
            main()
        }
    }

    // Forces stop of the billing service
    fun stop(force: Boolean) {
        logger.info("BillingService stopping")
        when {
            !force -> cancellationToken = true
            else -> {
                job?.cancel()
                runBlocking {
                    job?.join()
                }
            }
        }

        logger.info("BillingService stopped")
    }

    // Controls the execution of the service
    protected open fun cancellationToken() : Boolean {
        return cancellationToken
    }

    protected open suspend fun main() {
        while(!this.cancellationToken()) {
            this.mainRoutine()
            this.iterationsCount++
        }
    }

    // Main logic of the service
    private suspend fun mainRoutine() {
        this.logger.info("Entering main routine")

        // If it is not the 1st of the month go idle
        if (!dateTimeProvider.isFirstOfTheMonth()) {
            val intervalInMs = calculateSleepIntervalToNextMonth()
            this.timeOutProvider.sleep(intervalInMs)
            return
        }

        // Otherwise fetch the invoices to process
        val pendingInvoices = invoiceService.fetchAllByStatus(InvoiceStatus.PENDING)

        // Process each invoice sequentially
        for (invoice in pendingInvoices) {
            this.logger.info("Processing invoice: id ${invoice.id}")
            val isSuccess : Boolean = try {
                // Try to process the invoice
                this.paymentProvider.charge(invoice)
            } catch (e: CustomerNotFoundException) {
                handleCustomerNotFoundException(invoice)
            } catch (e: CurrencyMismatchException) {
                handleCurrencyMismatchException(invoice)
            } catch (e: NetworkException) {
                handleNetworkException(invoice)
                return
            }

            when {
                isSuccess -> handleSuccess(invoice)
                else -> handleFailure(invoice)
            }
        }

        // Will check in some minutes if new invoices show up
        this.timeOutProvider.sleep(this.iterationSleepTime)
    }

    // Handle successful processing of the invoice, update invoice status in the database
    private fun handleSuccess(invoice: Invoice) {
        this.logger.info("Invoice successfully processed: id ${invoice.id}")
        val updatedInvoice = invoiceService.updateStatusById(invoice.id, InvoiceStatus.PAID)
        if (updatedInvoice.status == InvoiceStatus.PAID) {
            this.logger.info("Invoice marked as status paid: id ${updatedInvoice.id}")
        } else {
            this.logger.info("Failed to update invoice: id ${invoice.id}")
        }
    }

    // Handle unsuccessful processing of the invoice, just log a generic error
    private fun handleFailure(invoice: Invoice) {
        this.logger.error("Ouch! Invoice processing failed: id ${invoice.id}")
    }

    // Handle exceptional case with mismatch of invoice currency and customer currency
    // convert the invoice amount to the new currency and update the database
    private fun handleCurrencyMismatchException(invoice: Invoice): Boolean {
        val customer = customerService.fetch(invoice.customerId)
        this.logger.warn("Failed to process invoice with id ${invoice.id}, currency ${invoice.amount.currency}: customer id ${customer?.id} currency ${customer?.currency}")
        invoiceService.updateCurrencyById(invoice.id, invoice.amount.currency)
        return false
    }

    // Handle exceptional case with missing customer, just log an error
    private fun handleCustomerNotFoundException(invoice: Invoice): Boolean {
        this.logger.error("Failed to process invoice with id ${invoice.id}, customer ${invoice.customerId} not found")
        return false
    }

    // Handle exceptional case with network error during processing of the invoice,
    // log an error and try again after some time
    private suspend fun handleNetworkException(invoice: Invoice) {
        this.logger.warn("Network outage while processing invoice ${invoice.id}: retrying in 60 seconds")
        this.timeOutProvider.sleep(this.networkOutageSleepTime)
    }

    // Helper method for calculating idle time until the next 1st of the month
    private fun calculateSleepIntervalToNextMonth(): Long {
        val nextFirstOfTheMonth = dateTimeProvider.nextFirstOfTheMonth()
        return Duration.between(dateTimeProvider.now(), nextFirstOfTheMonth).toMillis()
    }
}