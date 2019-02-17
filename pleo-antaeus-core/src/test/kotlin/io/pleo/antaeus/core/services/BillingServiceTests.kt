package io.pleo.antaeus.core.services

import com.kizitonwose.time.days
import io.mockk.*
import io.pleo.antaeus.core.contracts.IDateTimeProvider
import io.pleo.antaeus.core.contracts.ITimeOutProvider
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.helpers.Logger
import io.pleo.antaeus.core.services.artifacts.BillingServiceTestable
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime



class BillingServiceTests {

    @Test
    fun `BillingService can process 2 pending invoices`() {

        // Arrange
        val fixture = TestFixture()

        fixture.dal = mockk {
            every { fetchInvoicesByStatus(InvoiceStatus.PENDING) } returns fixture.pendingInvoices
            every { updateInvoiceStatus(1, any()) } returns fixture.paidInvoices[0]
            every { updateInvoiceStatus(2, any()) } returns fixture.paidInvoices[1]
        }

        fixture.timeOutProvider = mockk {
            coEvery { sleep(any()) } returns Unit
        }

        fixture.paymentProvider = mockk {
            every { charge(any()) } returns true
        }

        val billingService = BillingServiceTestable(
                fixture.dal,
                fixture.dateTimeProvider,
                fixture.timeOutProvider,
                fixture.paymentProvider,
                fixture.logger)
                .withMaxIterations(1)

        // Act
        runBlocking {
            billingService.main()
        }

        // Assert
        verify(exactly = 2) { fixture.paymentProvider.charge(any())}
        coVerify(exactly = 1) { fixture.timeOutProvider.sleep(billingService.iterationSleepTime)}
    }

    @Test
    fun `BillingService with network outage succeeds invoice processing 2nd attempt`() {

        // Arrange
        val fixture = TestFixture()

        fixture.dal = mockk {
            every { fetchInvoicesByStatus(InvoiceStatus.PENDING) } returns listOf(fixture.pendingInvoices[0])
            every { updateInvoiceStatus(1, any()) } returns fixture.paidInvoices[0]
        }

        fixture.paymentProvider = mockk {
            every { charge(invoice = fixture.pendingInvoices[0]) } throws NetworkException() andThen true
        }

        fixture.timeOutProvider = mockk {
            coEvery{ sleep(any()) } returns Unit
        }

        val billingService = BillingServiceTestable(
                fixture.dal,
                fixture.dateTimeProvider,
                fixture.timeOutProvider,
                fixture.paymentProvider,
                fixture.logger)
                .withMaxIterations(2)

        // Act
        runBlocking {
            billingService.main()
        }

        // Assert
        val slot = slot<String>()
        verify(atLeast = 1) { fixture.logger.warn(msg = capture(slot)) }
        assert(slot.captured.contains(fixture.pendingInvoices[0].id.toString()))

        coVerify(exactly = 1) { fixture.timeOutProvider.sleep(billingService.networkOutageSleepTime)}
        verify(exactly = 2) { fixture.paymentProvider.charge(any())}
    }

    @Test
    fun `BillingService waits until the first day of any month to run`() {

        // Arrange
        val fixture = TestFixture()

        fixture.timeOutProvider = mockk {
            coEvery{ sleep(any()) } returns Unit
        }

        fixture.dateTimeProvider = mockk {
            every { isFirstOfTheMonth() } returns false
            every { now() } returns LocalDateTime.of(2016, 2, 15, 12, 0) // Kotlin 1.0's birthday
            every { nextFirstOfTheMonth() } returns LocalDateTime.of(2016, 3, 1, 12, 0)
        }

        val billingService = BillingServiceTestable(
                fixture.dal,
                fixture.dateTimeProvider,
                fixture.timeOutProvider,
                fixture.paymentProvider,
                fixture.logger)
                .withMaxIterations(1)

        // Act
        runBlocking {
            billingService.main()
        }

        // Assert
        val slot = slot<Long>()
        coVerify(exactly = 1) { fixture.timeOutProvider.sleep(capture(slot))}
        assert(slot.captured == 15.days.inMilliseconds.longValue)
    }

    @Test
    fun `BillingService fails to charge a non-existing customer`() {
        // Arrange
        val fixture = TestFixture()

        fixture.dal = mockk {
            every { fetchInvoicesByStatus(InvoiceStatus.PENDING) } returns fixture.pendingInvoices
            every { updateInvoiceStatus(2, any()) } returns fixture.paidInvoices[1]
        }

        fixture.timeOutProvider = mockk {
            coEvery { sleep(any()) } returns Unit
        }

        fixture.paymentProvider = mockk {
            every { charge(any()) } throws CustomerNotFoundException(fixture.pendingInvoices.first().customerId) andThen true
        }

        val billingService = BillingServiceTestable(
                fixture.dal,
                fixture.dateTimeProvider,
                fixture.timeOutProvider,
                fixture.paymentProvider,
                fixture.logger)
                .withMaxIterations(1)

        // Act
        runBlocking {
            billingService.main()
        }

        // Assert
        val argumentsList = mutableListOf<String>()
        verify(atLeast = 2) { fixture.logger.error(msg = capture(argumentsList)) }
        assert(argumentsList.count() == 2)
        assert(argumentsList[0].contains(fixture.pendingInvoices[0].id.toString()))
        assert(argumentsList[0].contains(fixture.pendingInvoices[0].customerId.toString()))
        assert(argumentsList[1].contains(fixture.pendingInvoices[0].id.toString()))

        verify(exactly = 2) { fixture.paymentProvider.charge(any())}
        coVerify(exactly = 1) { fixture.timeOutProvider.sleep(billingService.iterationSleepTime)}
    }
}

class TestFixture {

    val pendingInvoices = listOf(
            Invoice(1, 98, Money(BigDecimal.valueOf(42), Currency.DKK), InvoiceStatus.PENDING),
            Invoice(2, 99, Money(BigDecimal.valueOf(24), Currency.DKK), InvoiceStatus.PENDING)
    )

    val paidInvoices = listOf(
            Invoice(1, 98, Money(BigDecimal.valueOf(42), Currency.DKK), InvoiceStatus.PAID),
            Invoice(2, 99, Money(BigDecimal.valueOf(24), Currency.DKK), InvoiceStatus.PAID)
    )

    var dal = mockk<AntaeusDal> {}

    var timeOutProvider = mockk<ITimeOutProvider> {}

    var paymentProvider = mockk<PaymentProvider> {}

    var dateTimeProvider = mockk<IDateTimeProvider> {
        every { isFirstOfTheMonth() } returns true
    }

    val logger = spyk<Logger> {}
}