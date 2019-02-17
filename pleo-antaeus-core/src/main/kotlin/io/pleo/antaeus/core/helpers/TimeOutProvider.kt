package io.pleo.antaeus.core.helpers

import io.pleo.antaeus.core.contracts.ITimeOutProvider
import kotlinx.coroutines.delay
import mu.KotlinLogging

/*
    TimeOutProvider: gives a way to control how we suspend the execution and set the thread in idle mode
 */
open class TimeOutProvider : ITimeOutProvider {

    private val logger = KotlinLogging.logger {}

    override suspend fun sleep(timeInSeconds: Long) {
        logger.info { "Going to sleep for $timeInSeconds seconds" }
        delay(timeInSeconds * 1000L)
    }
}