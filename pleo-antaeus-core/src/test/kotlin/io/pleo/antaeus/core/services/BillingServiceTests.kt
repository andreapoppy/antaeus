package io.pleo.antaeus.core.services

import io.mockk.*
import io.pleo.antaeus.core.contracts.IDateTimeProvider
import io.pleo.antaeus.core.contracts.ITimeOutProvider
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

class BillingServiceTests {

    @Test
    fun `BillingService can process 2 pending invoices`() {

        // Arrange
        val fixture = TestFixture()

        val dal = mockk<AntaeusDal> {
            every { fetchInvoicesByStatus(InvoiceStatus.PENDING) } returns fixture.pendingInvoices
            every { updateInvoiceStatus(1, any()) } returns fixture.paidInvoices[0]
            every { updateInvoiceStatus(2, any()) } returns fixture.paidInvoices[1]
        }

        val timeOutProvider = mockk<ITimeOutProvider> {
            coEvery { sleep(any()) } returns Unit
        }

        val paymentProvider = mockk<PaymentProvider> {
            every { charge(any()) } returns true
        }

        val billingService = BillingServiceTestable(
                dal,
                fixture.dateTimeProvider,
                timeOutProvider,
                paymentProvider,
                fixture.logger)
                .withMaxIterations(1)

        // Act
        runBlocking {
            billingService.main()
        }

        // Assert
        verify(exactly = 2) { paymentProvider.charge(any())}
        coVerify(exactly = 1) { timeOutProvider.sleep(5 * 60)}
    }

    @Test
    fun `BillingService with network outage succeeds invoice processing 2nd attempt`() {

        // Arrange
        val fixture = TestFixture()

        val dal = mockk<AntaeusDal> {
            every { fetchInvoicesByStatus(InvoiceStatus.PENDING) } returns listOf(fixture.pendingInvoices[0])
            every { updateInvoiceStatus(1, any()) } returns fixture.paidInvoices[0]
        }

        val paymentProvider = mockk<PaymentProvider> {
            every { charge(invoice = fixture.pendingInvoices[0]) } throws NetworkException() andThen true
        }

        val timeOutProvider = mockk<ITimeOutProvider> {
            coEvery{ sleep(any()) } returns Unit
        }

        val billingService = BillingServiceTestable(
                dal,
                fixture.dateTimeProvider,
                timeOutProvider,
                paymentProvider,
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
        coVerify(exactly = 1) { timeOutProvider.sleep(1 * 60)}
        verify(exactly = 2) { paymentProvider.charge(any())}
    }
}

class TestFixture {

    val pendingInvoices = listOf(
            Invoice(1, 1, Money(BigDecimal.valueOf(42), Currency.DKK), InvoiceStatus.PENDING),
            Invoice(2, 2, Money(BigDecimal.valueOf(42), Currency.DKK), InvoiceStatus.PENDING)
    )

    val paidInvoices = listOf(
            Invoice(1, 1, Money(BigDecimal.valueOf(42), Currency.DKK), InvoiceStatus.PAID),
            Invoice(2, 2, Money(BigDecimal.valueOf(42), Currency.DKK), InvoiceStatus.PAID)
    )

    val dateTimeProvider = mockk<IDateTimeProvider> {
        every { isFirstOfTheMonth() } returns true
    }

    val logger = spyk<Logger> {}
}