package io.pleo.antaeus.core.services

import com.kizitonwose.time.days
import io.mockk.*
import io.pleo.antaeus.core.contracts.IDateTimeProvider
import io.pleo.antaeus.core.contracts.ITimeOutProvider
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.helpers.Logger
import io.pleo.antaeus.core.services.artifacts.BillingServiceTestable
import io.pleo.antaeus.models.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/*
    Suite of tests for the _BillingService_
    These tests are "scenario tests", they cover essential scenarios in our application for what concerns the processing
    of invoices. Every tests mocks specific dependencies in order to control the execution flow.

    The _TestFixture_ is an helper class that let us re-use basic initializations of the mocked services.

    These are the scenarios covered:
    - The _BillingService_ can process 2 standard invoices
    - The _BillingService_ can process 1 invoice successfully after recovering from a network outage
    - The _BillingService_ can process invoices only on the 1st day of any month
    - The _BillingService_ logs an error when the customer for a target invoice does not exist
    - The _BillingService_ can process 1 invoice successfully after trying with a wrong currency
 */

class BillingServiceTests {

    @Test
    fun `BillingService can process 2 pending invoices`() {

        // Arrange
        val fixture = TestFixture()

        fixture.invoiceService = mockk {
            every { fetchAllByStatus(InvoiceStatus.PENDING) } returns fixture.pendingInvoices
            every { updateStatusById(1, any()) } returns fixture.paidInvoices[0]
            every { updateStatusById(2, any()) } returns fixture.paidInvoices[1]
        }

        fixture.paymentProvider = mockk {
            every { charge(any()) } returns true
        }

        val billingService = BillingServiceTestable(
                fixture.invoiceService,
                fixture.customerService,
                fixture.dateTimeProvider,
                fixture.timeOutProvider,
                fixture.paymentProvider,
                fixture.logger)
                .withMaxIterations(1)

        runBlocking {
            // Act
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

        fixture.invoiceService = mockk {
            every { fetchAllByStatus(InvoiceStatus.PENDING) } returns listOf(fixture.pendingInvoices[0])
            every { updateStatusById(1, any()) } returns fixture.paidInvoices[0]
        }

        fixture.paymentProvider = mockk {
            every { charge(invoice = fixture.pendingInvoices[0]) } throws NetworkException() andThen true
        }

        val billingService = BillingServiceTestable(
                fixture.invoiceService,
                fixture.customerService,
                fixture.dateTimeProvider,
                fixture.timeOutProvider,
                fixture.paymentProvider,
                fixture.logger)
                .withMaxIterations(2)

        runBlocking {
            // Act
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

        fixture.dateTimeProvider = mockk {
            every { isFirstOfTheMonth() } returns false
            every { now() } returns LocalDateTime.of(2016, 2, 15, 12, 0) // Kotlin 1.0's birthday
            every { nextFirstOfTheMonth() } returns LocalDateTime.of(2016, 3, 1, 12, 0)
        }

        val billingService = BillingServiceTestable(
                fixture.invoiceService,
                fixture.customerService,
                fixture.dateTimeProvider,
                fixture.timeOutProvider,
                fixture.paymentProvider,
                fixture.logger)
                .withMaxIterations(1)

        runBlocking {
            // Act
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

        fixture.invoiceService = mockk {
            every { fetchAllByStatus(InvoiceStatus.PENDING) } returns fixture.pendingInvoices
            every { updateStatusById(2, any()) } returns fixture.paidInvoices[1]
        }

        fixture.paymentProvider = mockk {
            every { charge(any()) } throws CustomerNotFoundException(fixture.pendingInvoices.first().customerId) andThen true
        }

        val billingService = BillingServiceTestable(
                fixture.invoiceService,
                fixture.customerService,
                fixture.dateTimeProvider,
                fixture.timeOutProvider,
                fixture.paymentProvider,
                fixture.logger)
                .withMaxIterations(1)

        runBlocking {
            // Act
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

    @Test
    fun `BillingService reacts to currency mismatch exceptions`() {

        // Arrange
        val fixture = TestFixture()
        val customerId = fixture.pendingInvoices[0].customerId
        val invoice = fixture.pendingInvoices[0]
        val convertedInvoice = Invoice(1, 98, Money(BigDecimal(5.63), Currency.EUR), InvoiceStatus.PENDING)

        fixture.customerService = mockk {
            every { fetch(customerId) } returns Customer(customerId, Currency.EUR)
        }

        fixture.invoiceService = mockk {
            every { fetchAllByStatus(InvoiceStatus.PENDING) } returns listOf(invoice)
            every { updateCurrencyById(1, invoice.amount.currency) } returns convertedInvoice
            every { updateStatusById(1, any()) } returns fixture.paidInvoices[0]
        }

        fixture.paymentProvider = mockk {
            every { charge(any()) } throws CurrencyMismatchException(fixture.pendingInvoices.first().id, fixture.pendingInvoices.first().customerId) andThen true
        }

        val billingService = BillingServiceTestable(
                fixture.invoiceService,
                fixture.customerService,
                fixture.dateTimeProvider,
                fixture.timeOutProvider,
                fixture.paymentProvider,
                fixture.logger)
                .withMaxIterations(2)

        runBlocking {
            // Act
            billingService.main()
        }

        // Assert

        // Failed to process invoice with id 1, currency DKK: customer id 98 currency EUR
        val argumentToWarningMessage = slot<String>()
        verify(atLeast = 1) { fixture.logger.warn(msg = capture(argumentToWarningMessage)) }
        assert(argumentToWarningMessage.captured.contains(fixture.pendingInvoices[0].id.toString()))
        assert(argumentToWarningMessage.captured.contains(fixture.pendingInvoices[0].customerId.toString()))

        // Ouch! Invoice processing failed: id 1
        val argumentToFailMessage = slot<String>()
        verify(atLeast = 1) { fixture.logger.error(msg = capture(argumentToFailMessage)) }
        assert(argumentToFailMessage.captured.contains(fixture.pendingInvoices[0].id.toString()))

        verify(exactly = 2) { fixture.paymentProvider.charge(any())}
        coVerify(exactly = 2) { fixture.timeOutProvider.sleep(billingService.iterationSleepTime)}
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

    var invoiceService = mockk<InvoiceService> {}
    var customerService = mockk<CustomerService> {}
    var paymentProvider = mockk<PaymentProvider> {}
    var dateTimeProvider = mockk<IDateTimeProvider> {
        every { isFirstOfTheMonth() } returns true
    }
    var timeOutProvider = mockk<ITimeOutProvider> {
        coEvery { sleep(any()) } returns Unit
    }
    val logger = spyk<Logger> {}
}