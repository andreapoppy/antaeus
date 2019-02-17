## Candidate: Andrea Del Popolo

Date: 17-02-2019

Contacts: andrea.del.popolo@hotmail.it | [LinkedIn](https://www.linkedin.com/in/andrea-del-popolo/)

### Notes:

It is the first time that I work in Kotlin. Overall, I found it interesting and plesant to work with. My background is in .NET, C# and Visual Studio, for this solution I chose IntelliJ Idea Community Edition as IDE and JVM 8 running on Windows 10 Home. I have encountered many blocking issues along the way, but I was able to figure a way out using the many resources available online ;-)

### How does my solution address the challenge?

In my solution I use the [coroutine feature](https://kotlinlang.org/docs/reference/coroutines/coroutines-guide.html) of the Kotlin language, it allows to create a background thread that runs only my logic for processing the invoices.

The *BillingService* is initialized by the main function of the module *pleo-antaeus-app* upon start.
Once the service spins up, it checks if it is the 1st of the month, if it is not then the service remains idle until the next 1st day of the next month.

When it is the 1st of the month, then the service processes the invoices that are stored in the database in a sequential fashion. It continues to process invoices until the end of the day, should new invoices be stored during the day.

The *BillingService* depends on the following:
* The invoice service
* The customer service
* An IDateTimeProvider in order to control time-related logic
* An ITimeOutProvider in order to control how the billing service goes in idle mode
* A Payment provider for the actual processing of the invoices (external)
* A ILogger implementation for providing basic logging features

I have decided to inject the above dependencies in the constructor, this allows me to easily test the logic of the service by controlling the mocked dependencies, as well as to keep track of the actual responsibilities of the service.

### Limitations of the solution

There are many limitations in my basic solution, most importantly, the *BillingService* processes the invoices in a sequential fashion, this limitation could be improved by introducing some degree of parallelism in the logic. In .NET, in order to solve this nature of problems I have used the [TLP Library](https://docs.microsoft.com/en-us/dotnet/standard/parallel-programming/task-parallel-library-tpl) in the past, which provides an elegant and fluent API for managing pipelines and data flows in parallel, it would be interesting to have something like this library in Kotlin. As food for thoughts, it would be also interesting to refactor any logic for processing invoices to a cloud service, for example Azure Functions or AWS Lambdas.

A second important limitation is the simple way I use for converting currencies, in a real system I would expect a formal way for converting the amounts of the invoices, mayby by relying on a third party service.

### Side notes

I have added some additional libraries to the skeleton implementation:

* [com.kizitonwose](https://github.com/kizitonwose/Time) Type-safe time calculations in Kotlin, powered by generics.
* [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) Library support for Kotlin coroutines
* [MicroUtils/kotlin-logging]() Lightweight logging framework for Kotlin. A convenient and performant logging library wrapping slf4j with Kotlin extensions

I was not able to run Docker, since my machine is running a version of Windows Home, I cannot guarantee that Docker will still work after my changes.

----------------------------------------------------------------

## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will pay those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

### Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── pleo-antaeus-app
|
|       Packages containing the main() application. 
|       This is where all the dependencies are instantiated.
|
├── pleo-antaeus-core
|
|       This is where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|
|       Module interfacing with the database. Contains the models, mappings and access layer.
|
├── pleo-antaeus-models
|
|       Definition of models used throughout the application.
|
├── pleo-antaeus-rest
|
|        Entry point for REST API. This is where the routes are defined.
└──
```

## Instructions
Fork this repo with your solution. We want to see your progression through commits (don’t commit the entire solution in 1 step) and don't forget to create a README.md to explain your thought process.

Happy hacking 😁!

## How to run
```
./docker-start.sh
```

## Libraries currently in use
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
