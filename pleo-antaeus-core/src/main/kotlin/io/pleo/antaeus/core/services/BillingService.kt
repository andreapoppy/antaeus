package io.pleo.antaeus.core.services

import com.kizitonwose.time.minutes
import io.pleo.antaeus.core.contracts.IDateTimeProvider
import io.pleo.antaeus.core.contracts.ILogger
import io.pleo.antaeus.core.contracts.ITimeOutProvider
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*
import java.time.Duration

open class BillingService(
        private val invoiceService: InvoiceService,
        private val customerService: CustomerService,
        private val dateTimeProvider: IDateTimeProvider,
        private val timeOutProvider: ITimeOutProvider,
        private val paymentProvider: PaymentProvider,
        private val logger : ILogger) {

    private var job : Job? = null
    protected var iterationsCount : Int = 0
    protected open val iterationSleepTime : Long = 5.minutes.inSeconds.longValue
    protected open val networkOutageSleepTime : Long = 1.minutes.inSeconds.longValue

    fun run() {
        logger.info("BillingService running")
        job = GlobalScope.launch {
            main()
        }
    }

    fun stop() {
        logger.info("BillingService stopping")
        job?.cancel()
        runBlocking {
            job?.join()
        }
        logger.info("BillingService stopped")
    }

    protected open fun cancellationToken() : Boolean {
        return false
    }

    protected open suspend fun main() {
        while(!this.cancellationToken()) {
            this.mainRoutine()
            this.iterationsCount++
        }
    }

    private suspend fun mainRoutine() {
        this.logger.info("Entering main routine")

        if (!dateTimeProvider.isFirstOfTheMonth()) {
            val intervalInMs = calculateSleepIntervalToNextMonth()
            this.timeOutProvider.sleep(intervalInMs)
            return
        }

        val pendingInvoices = invoiceService.fetchAllByStatus(InvoiceStatus.PENDING)
        for (invoice in pendingInvoices) {

            this.logger.info("Processing invoice: id ${invoice.id}")

            var isSuccess : Boolean
            try {
                isSuccess = this.paymentProvider.charge(invoice)
            } catch (e: CustomerNotFoundException) {
                this.logger.error("Failed to process invoice with id ${invoice.id}, customer ${invoice.customerId} not found")
                isSuccess = false
            } catch (e: CurrencyMismatchException) {
                // TODO: figure out if we can fix the currency and retry
                val customer = customerService.fetch(invoice.customerId)
                this.logger.warn("Failed to process invoice with id ${invoice.id}, currency ${invoice.amount.currency}: customer id ${customer?.id} currency ${customer?.currency}")
                isSuccess = false
            } catch (e: NetworkException) {
                // Retry later
                this.logger.warn("Network outage while processing invoice ${invoice.id}: retrying in 60 seconds")
                this.timeOutProvider.sleep(this.networkOutageSleepTime)
                return
            }

            if (isSuccess) {
                this.logger.info("Invoice successfully processed: id ${invoice.id}")
                val updatedInvoice = invoiceService.updateStatusById(invoice.id, InvoiceStatus.PAID)
                if (updatedInvoice.status == InvoiceStatus.PAID) {
                    this.logger.info("Invoice marked as status paid: id ${updatedInvoice.id}")
                } else {
                    this.logger.info("Failed to update invoice: id ${invoice.id}")
                }
            } else {
                this.logger.error("Ouch! Invoice processing failed: id ${invoice.id}")
            }
        }

        // Will check in some minutes if new invoices show up
        this.timeOutProvider.sleep(this.iterationSleepTime)
    }

    private fun calculateSleepIntervalToNextMonth(): Long {
        val nextFirstOfTheMonth = dateTimeProvider.nextFirstOfTheMonth()
        return Duration.between(dateTimeProvider.now(), nextFirstOfTheMonth).toMillis()
    }
}