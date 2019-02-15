package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.contracts.IDateTimeProvider
import io.pleo.antaeus.core.contracts.ITimeOutProvider
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.time.Duration

open class BillingService(
        private val dal: AntaeusDal,
        private val dateTimeProvider: IDateTimeProvider,
        private val timeOutProvider: ITimeOutProvider,
        private val paymentProvider: PaymentProvider) {

    private var job : Job? = null
    private val logger = KotlinLogging.logger {}

    fun run() {
       job = GlobalScope.launch {
           while(true) {
               mainRoutine()
           }
       }
    }

    protected open suspend fun mainRoutine() {

        logger.info("Entering mainRoutine")

        if (!dateTimeProvider.isFirstOfTheMonth()) {
            val intervalInMs = calculateSleepIntervalToNextMonth()
            timeOutProvider.sleep(intervalInMs)
        }

        val pendingInvoices = dal.fetchInvoicesByStatus(InvoiceStatus.PENDING)
        for (invoice in pendingInvoices) {

            logger.info("Processing invoice with id ${invoice.id}")

            var isSuccess = false
            try {
                isSuccess = paymentProvider.charge(invoice)
            }
            catch(e : CustomerNotFoundException) {
                logger.error("Failed to process invoice with id ${invoice.id}: customer ${invoice.customerId} not found")
                isSuccess = false
            }
            catch(e : CurrencyMismatchException) {
                // TODO: figure out if we can fix the currency and retry
                val customer = dal.fetchCustomer(invoice.id)
                logger.warn("Failed to process invoice with id ${invoice.id}, currency ${invoice.amount.currency}: customer id ${customer?.id} currency ${customer?.currency}")
                isSuccess = false
            }
            catch(e : NetworkException) {
                // Retry later
                logger.warn("Network outage while processing invoice ${invoice.id}: retrying in 60 seconds")
                timeOutProvider.sleep(60)
                restart()
            }

            if (isSuccess) {
                logger.info("Invoice with id ${invoice.id} successfully processed")
                val updatedInvoice = dal.updateInvoiceStatus(invoice.id, InvoiceStatus.PAID)
                if (updatedInvoice != null && updatedInvoice.status == InvoiceStatus.PAID) {
                    logger.info("Invoice with id ${updatedInvoice.id} marked as status paid")
                }
                else {
                    logger.info("Failed to update invoice with id ${invoice.id}")
                }
            }
            else {
                logger.error("Ouch! Invoice processing failed: id ${invoice.id}")
            }
        }

        // Will check in 5 minutes if new invoices show up
        timeOutProvider.sleep(5 * 60)
    }

    private suspend fun restart() {
        logger.info("BillingService restarting")
        job?.cancel()
        job?.join()
        run()
    }

    private fun calculateSleepIntervalToNextMonth(): Long {
        val nextFirstOfTheMonth = dateTimeProvider.nextFirstOfTheMonth()
        return Duration.between(nextFirstOfTheMonth, dateTimeProvider.now()).toMillis()
    }
}