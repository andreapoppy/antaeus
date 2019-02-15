package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.contracts.IDateTimeProvider
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import kotlinx.coroutines.*
import java.time.Duration

class BillingService(
        private val dal: AntaeusDal,
        private val dateTimeProvider: IDateTimeProvider,
        private val paymentProvider: PaymentProvider) {
   fun run() {
       GlobalScope.launch {
           mainRoutine()
       }
   }

    private suspend fun mainRoutine() {
        while (true) {

            if (!dateTimeProvider.isFirstOfTheMonth()) {
                val nextFirstOfTheMonth = dateTimeProvider.nextFirstOfTheMonth()
                val intervalInMs = Duration.between(nextFirstOfTheMonth, dateTimeProvider.now()).toMillis()
                goToSleep(intervalInMs)
            }

            // TODO: pick up from here
        }
    }

    private suspend fun goToSleep(timeInMs : Long) {
        println("BillingService is going to sleep in the background")
        delay(timeInMs)
    }
}