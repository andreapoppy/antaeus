package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.contracts.IDateTimeProvider
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*
import java.time.Duration

class BillingService(
        private val dal: AntaeusDal,
        private val dateTimeProvider: IDateTimeProvider,
        private val paymentProvider: PaymentProvider) {

    private var job : Job? = null

    fun run() {
       job = GlobalScope.launch {
           mainRoutine()
       }
   }

    private suspend fun restart() {
        if (job != null) {
            job?.cancel()
            job?.join()
        }
        job = GlobalScope.launch {
            mainRoutine()
        }
    }

    private suspend fun mainRoutine() {
        while (true) {
            if (!dateTimeProvider.isFirstOfTheMonth()) {
                val intervalInMs = calculateSleepIntervalToNextMonth()
                goToSleep(intervalInMs)
            }

            val pendingInvoices = dal.fetchInvoicesByStatus(InvoiceStatus.PENDING)
            for (invoice in pendingInvoices) {
                var isSuccess = false
                try {
                    isSuccess = paymentProvider.charge(invoice)
                }
                catch(e : CustomerNotFoundException) {
                    // TODO: log and continue
                    isSuccess = false
                }
                catch(e : CurrencyMismatchException) {
                    // TODO: figure out if we can fix the currency
                    isSuccess = false
                }
                catch(e : NetworkException) {
                    // Retry later
                    goToSleep(60 * 1000L)
                    restart()
                }

                if (isSuccess) {
                    dal.updateInvoiceStatus(invoice.id, InvoiceStatus.PAID)
                }
                else {
                    // TODO: log
                }
            }

            // Will check in 5 minutes if new invoices show up
            goToSleep(5 * 60 * 1000L)
        }
    }

    private fun calculateSleepIntervalToNextMonth(): Long {
        val nextFirstOfTheMonth = dateTimeProvider.nextFirstOfTheMonth()
        return Duration.between(nextFirstOfTheMonth, dateTimeProvider.now()).toMillis()
    }

    private suspend fun goToSleep(timeInMs : Long) {
        println("BillingService is going to sleep in the background")
        delay(timeInMs)
    }
}