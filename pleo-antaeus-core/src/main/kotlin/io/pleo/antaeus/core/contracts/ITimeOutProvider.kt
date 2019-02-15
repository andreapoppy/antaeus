package io.pleo.antaeus.core.contracts

interface ITimeOutProvider {
    suspend fun sleep(TimeInSeconds: Long)
}