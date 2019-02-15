package io.pleo.antaeus.core.services

import io.mockk.*
import io.pleo.antaeus.core.contracts.IDateTimeProvider
import io.pleo.antaeus.core.contracts.ITimeOutProvider
import io.pleo.antaeus.core.external.PaymentProvider
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
        val billingService = BillingServiceTestable(fixture.dal, fixture.dateTimeProvider, fixture.timeOutProvider, fixture.paymentProvider)

        // Act
        runBlocking {
            billingService.mainRoutine()
        }

        // Assert
        verify(exactly = 2) { fixture.paymentProvider.charge(any())}
        coVerify(exactly = 1) { fixture.timeOutProvider.sleep(5 * 60 * 1000L)}
    }
}

class TestFixture {

    private var pendingInvoices = listOf(
            Invoice(1, 1, Money(BigDecimal.valueOf(42), Currency.DKK), InvoiceStatus.PENDING),
            Invoice(2, 2, Money(BigDecimal.valueOf(42), Currency.DKK), InvoiceStatus.PENDING)
    )

    var paidInvoices = listOf(
            Invoice(1, 1, Money(BigDecimal.valueOf(42), Currency.DKK), InvoiceStatus.PAID),
            Invoice(2, 2, Money(BigDecimal.valueOf(42), Currency.DKK), InvoiceStatus.PAID)
    )

    val dal = mockk<AntaeusDal> {
        every { fetchInvoicesByStatus(InvoiceStatus.PENDING) } returns pendingInvoices
        every { updateInvoiceStatus(1, any()) } returns paidInvoices[0]
        every { updateInvoiceStatus(2, any()) } returns paidInvoices[1]
    }

    val dateTimeProvider = mockk<IDateTimeProvider> {
        every { isFirstOfTheMonth() } returns true
    }

    val timeOutProvider = mockk<ITimeOutProvider>() {
        coEvery { sleep(any()) } returns Unit
    }

    val paymentProvider = mockk<PaymentProvider> {
        every { charge(any()) } returns true
    }
}

class BillingServiceTestable(
        dal: AntaeusDal,
        dateTimeProvider: IDateTimeProvider,
        timeOutProvider: ITimeOutProvider,
        paymentProvider: PaymentProvider) : BillingService(dal, dateTimeProvider, timeOutProvider, paymentProvider) {

    public override suspend fun mainRoutine()
    {
        super.mainRoutine()
    }
}