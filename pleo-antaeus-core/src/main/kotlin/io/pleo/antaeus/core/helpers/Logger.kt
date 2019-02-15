package io.pleo.antaeus.core.helpers

import io.pleo.antaeus.core.contracts.ILogger
import mu.KotlinLogging

class Logger : ILogger{

    private val kotlinLogger = KotlinLogging.logger {}

    override fun info(msg: String) {
        kotlinLogger.info(msg)
    }

    override fun error(msg: String) {
        kotlinLogger.error(msg)
    }

    override fun warn(msg: String) {
        kotlinLogger.warn(msg)
    }
}