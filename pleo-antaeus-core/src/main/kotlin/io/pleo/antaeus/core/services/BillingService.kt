package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.contracts.IDateTimeProvider
import io.pleo.antaeus.core.contracts.ILogger
import io.pleo.antaeus.core.contracts.ITimeOutProvider
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*
import java.time.Duration

open class BillingService(
        private val dal: AntaeusDal,
        private val dateTimeProvider: IDateTimeProvider,
        private val timeOutProvider: ITimeOutProvider,
        private val paymentProvider: PaymentProvider,
        private val logger : ILogger) {

    private var job : Job? = null
    protected var iterationsCount : Int = 0

    fun run() {
        logger.info("BillingService running")
        job = GlobalScope.launch {
            main()
        }
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
        this.logger.info("Entering mainRoutine")

        if (!dateTimeProvider.isFirstOfTheMonth()) {
            val intervalInMs = calculateSleepIntervalToNextMonth()
            this.timeOutProvider.sleep(intervalInMs)
        }

        val pendingInvoices = dal.fetchInvoicesByStatus(InvoiceStatus.PENDING)
        for (invoice in pendingInvoices) {

            this.logger.info("Processing invoice with id ${invoice.id}")

            var isSuccess = false
            try {
                isSuccess = this.paymentProvider.charge(invoice)
            } catch (e: CustomerNotFoundException) {
                this.logger.error("Failed to process invoice with id ${invoice.id}, customer ${invoice.customerId} not found")
                isSuccess = false
            } catch (e: CurrencyMismatchException) {
                // TODO: figure out if we can fix the currency and retry
                val customer = dal.fetchCustomer(invoice.id)
                this.logger.warn("Failed to process invoice with id ${invoice.id}, currency ${invoice.amount.currency}: customer id ${customer?.id} currency ${customer?.currency}")
                isSuccess = false
            } catch (e: NetworkException) {
                // Retry later
                this.logger.warn("Network outage while processing invoice ${invoice.id}: retrying in 60 seconds")
                this.timeOutProvider.sleep(60)
                return
            }

            if (isSuccess) {
                this.logger.info("Invoice successfully processed: id ${invoice.id}")
                val updatedInvoice = dal.updateInvoiceStatus(invoice.id, InvoiceStatus.PAID)
                if (updatedInvoice != null && updatedInvoice.status == InvoiceStatus.PAID) {
                    this.logger.info("Invoice marked as status paid: id ${updatedInvoice.id}")
                } else {
                    this.logger.info("Failed to update invoice: id ${invoice.id}")
                }
            } else {
                this.logger.error("Ouch! Invoice processing failed: id ${invoice.id}")
            }
        }

        // Will check in 5 minutes if new invoices show up
        this.timeOutProvider.sleep(5 * 60)
    }

    private suspend fun stop() {
        logger.info("BillingService stopping")
        job?.cancel()
        job?.join()
        logger.info("BillingService stopped")
    }

    private fun calculateSleepIntervalToNextMonth(): Long {
        val nextFirstOfTheMonth = dateTimeProvider.nextFirstOfTheMonth()
        return Duration.between(nextFirstOfTheMonth, dateTimeProvider.now()).toMillis()
    }
}