/*
    Defines the main() entry point of the app.
    Configures the database and sets up the REST web service.
 */

@file:JvmName("AntaeusApp")

package io.pleo.antaeus.app

import getPaymentProvider
import io.pleo.antaeus.core.helpers.DateTimeProvider
import io.pleo.antaeus.core.helpers.Logger
import io.pleo.antaeus.core.helpers.TimeOutProvider
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CurrencyService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.data.CustomerTable
import io.pleo.antaeus.data.InvoiceTable
import io.pleo.antaeus.rest.AntaeusRest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import setupInitialData
import java.sql.Connection

fun main() {
    // The tables to create in the database.
    val tables = arrayOf(InvoiceTable, CustomerTable)

    // Connect to the database and create the needed tables. Drop any existing data.
    val db = Database
        .connect("jdbc:sqlite:/tmp/data.db", "org.sqlite.JDBC")
        .also {
            TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
            transaction(it) {
                addLogger(StdOutSqlLogger)
                // Drop all existing tables to ensure a clean slate on each run
                SchemaUtils.drop(*tables)
                // Create all tables
                SchemaUtils.create(*tables)
            }
        }

    // Set up data access layer.
    val dal = AntaeusDal(db = db)

    // Insert example data in the database.
    setupInitialData(dal = dal)

    // Get third parties
    val paymentProvider = getPaymentProvider()

    // Create core services
    var currencyService = CurrencyService()
    val invoiceService = InvoiceService(dal = dal, currencyService = currencyService)
    val customerService = CustomerService(dal = dal)

    // Create cross-cutting-concerns
    val dateTimeProvider = DateTimeProvider()
    val timeOutProvider = TimeOutProvider()
    val logger = Logger()

    // This is _my_ billing service
    val billingService = BillingService(
            invoiceService = invoiceService,
            customerService = customerService,
            dateTimeProvider = dateTimeProvider,
            timeOutProvider = timeOutProvider,
            paymentProvider = paymentProvider,
            logger = logger
    )

    billingService.run()

    // Create REST web service
    AntaeusRest(
        invoiceService = invoiceService,
        customerService = customerService
    ).run()
}

