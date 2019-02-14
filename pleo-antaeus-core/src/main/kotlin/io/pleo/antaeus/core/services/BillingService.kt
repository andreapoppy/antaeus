package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import kotlinx.coroutines.*

class BillingService(
    private val paymentProvider: PaymentProvider
) {
   fun run() {
       GlobalScope.launch {
          while(true) {
             delay(1000L)
             println("BillingService is running in the background")
          }
       }
   }
}