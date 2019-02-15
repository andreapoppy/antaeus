package io.pleo.antaeus.core.helpers

import io.pleo.antaeus.core.contracts.ITimeOutProvider
import kotlinx.coroutines.delay

class TimeOutProvider : ITimeOutProvider {
    override suspend fun sleep(timeInSeconds: Long) {
        delay(timeInSeconds * 1000L)
    }
}