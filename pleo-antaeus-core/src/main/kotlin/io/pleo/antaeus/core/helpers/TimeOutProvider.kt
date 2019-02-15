package io.pleo.antaeus.core.helpers

import io.pleo.antaeus.core.contracts.ITimeOutProvider
import kotlinx.coroutines.delay
import mu.KotlinLogging

class TimeOutProvider : ITimeOutProvider {

    private val logger = KotlinLogging.logger {}

    override suspend fun sleep(timeInSeconds: Long) {
        logger.info { "Going to sleep for $timeInSeconds seconds" }
        delay(timeInSeconds * 1000L)
    }
}