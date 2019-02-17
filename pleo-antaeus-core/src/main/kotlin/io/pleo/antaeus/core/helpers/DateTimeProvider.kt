package io.pleo.antaeus.core.helpers

import io.pleo.antaeus.core.contracts.IDateTimeProvider
import java.time.LocalDateTime
import java.time.ZoneId

/*
    DateTimeProvider: contains basic methods for allowing injection of logic for controlling time across the application
 */

class DateTimeProvider : IDateTimeProvider {
    override fun zoneId() : ZoneId {
        return ZoneId.of("UTC")
    }
    override fun now() : LocalDateTime {
        return LocalDateTime.now(zoneId())
    }
    override fun isFirstOfTheMonth() : Boolean {
        return now().dayOfMonth == 1
    }
    override fun nextFirstOfTheMonth() : LocalDateTime {
        val now = now()
        return when {
            now.monthValue != 12 -> LocalDateTime.of(now.year, now.monthValue + 1, 1, 0, 0)
            else -> LocalDateTime.of(now.year + 1, 1, 1, 0, 0)
        }
    }
}