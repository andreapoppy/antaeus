package io.pleo.antaeus.core.services.artifacts

import com.kizitonwose.time.minutes
import io.pleo.antaeus.core.contracts.IDateTimeProvider
import io.pleo.antaeus.core.contracts.ILogger
import io.pleo.antaeus.core.contracts.ITimeOutProvider
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.data.AntaeusDal

class BillingServiceTestable(
        invoiceService: InvoiceService,
        customerService: CustomerService,
        dateTimeProvider: IDateTimeProvider,
        timeOutProvider: ITimeOutProvider,
        paymentProvider: PaymentProvider,
        logger: ILogger)
    : BillingService(invoiceService, customerService, dateTimeProvider, timeOutProvider, paymentProvider, logger) {

    private var maxIterations : Int = 0

    public override val iterationSleepTime : Long = 5.minutes.inSeconds.longValue
    public override val networkOutageSleepTime : Long = 1.minutes.inSeconds.longValue

    public override suspend fun main()
    {
        super.main()
    }

    public override fun cancellationToken() : Boolean
    {
        if (this.maxIterations > 0 && this.iterationsCount >= this.maxIterations) {
            return true
        }

        return false
    }

    fun withMaxIterations(maxIterations : Int) : BillingServiceTestable {
        this.maxIterations = maxIterations
        return this
    }
}