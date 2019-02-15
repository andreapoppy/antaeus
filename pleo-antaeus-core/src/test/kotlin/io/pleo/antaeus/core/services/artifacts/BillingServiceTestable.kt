package io.pleo.antaeus.core.services.artifacts

import io.pleo.antaeus.core.contracts.IDateTimeProvider
import io.pleo.antaeus.core.contracts.ILogger
import io.pleo.antaeus.core.contracts.ITimeOutProvider
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.data.AntaeusDal

class BillingServiceTestable(
        dal: AntaeusDal,
        dateTimeProvider: IDateTimeProvider,
        timeOutProvider: ITimeOutProvider,
        paymentProvider: PaymentProvider,
        logger: ILogger)
    : BillingService(dal, dateTimeProvider, timeOutProvider, paymentProvider, logger) {

    private var maxIterations : Int = 0

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