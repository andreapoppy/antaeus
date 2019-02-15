package io.pleo.antaeus.core.contracts

import java.time.LocalDateTime
import java.time.ZoneId

interface IDateTimeProvider {
    fun now() : LocalDateTime
    fun zoneId() : ZoneId
    fun nextFirstOfTheMonth(): LocalDateTime
    fun isFirstOfTheMonth(): Boolean
}