package io.pleo.antaeus.core.contracts

interface ILogger {
    fun info(msg : String)
    fun error(msg: String)
    fun warn(msg: String)
}