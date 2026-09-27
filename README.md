# NairaWallet

A production-oriented digital wallet backend built with **Java and Spring Boot**, designed to demonstrate how core wallet and payment operations can be implemented with a strong focus on **transaction integrity, concurrency control, idempotency, auditability, and testability**.

NairaWallet simulates the backend of a Nigerian digital wallet where users can create wallets, deposit funds, withdraw funds, and transfer money between wallets.

The project is being developed incrementally with a focus on applying backend engineering and fintech concepts that are relevant to real-world financial systems.

---

## 🚀 Project Overview

NairaWallet provides backend APIs for managing users, wallets, and financial transactions.

The primary goal of the project is not simply to implement CRUD operations, but to explore the engineering challenges that arise when building systems that handle money.

The project currently focuses on:

* Wallet lifecycle management
* Deposits and withdrawals
* Wallet-to-wallet transfers
* Transaction state management
* Idempotency
* Optimistic locking
* Transactional consistency
* Double-entry-style ledger records
* Validation and exception handling
* Unit and integration testing
* PostgreSQL persistence
* Production-oriented domain design

---

## 🛠️ Technology Stack

| Technology                      | Purpose                         |
| ------------------------------- | ------------------------------- |
| **Java 17**                     | Backend programming language    |
| **Spring Boot**                 | Application framework           |
| **Spring Data JPA / Hibernate** | Persistence and ORM             |
| **PostgreSQL**                  | Relational database             |
| **Maven**                       | Build and dependency management |
| **JUnit 5**                     | Testing                         |
| **Mockito**                     | Unit testing and mocking        |
| **Lombok**                      | Boilerplate reduction           |
| **Git / GitHub**                | Version control                 |
| **Postman**                     | API testing                     |

---

## 🏗️ Architecture

The application follows a layered backend architecture:

```text
Client
  │
  ▼
Controller
  │
  ▼
Service
  │
  ├── Wallet Domain
  ├── Transaction Domain
  ├── Ledger
  └── Idempotency
  │
  ▼
Repository
  │
  ▼
PostgreSQL
```

The project separates responsibilities between controllers, services, repositories, and domain entities while keeping important wallet state transitions inside the domain model.

---

## 💰 Core Features

### User Management

Users can be created with:

* Full name
* Email
* Phone number
* User role
* Automatically created wallet

Each user has a one-to-one relationship with a wallet.

---

### Wallet Management

Wallets currently support:

* NGN currency
* Active/inactive status
* Balance management
* Wallet creation
* Credit operations
* Debit operations
* Insufficient-funds protection
* Inactive-wallet protection

Wallet state changes are controlled through domain methods rather than exposing unrestricted setters.

For example:

```java
wallet.credit(amount);
wallet.debit(amount);
wallet.deactivate();
```

This keeps important business rules close to the object that owns the state.

---

## 💸 Transactions

NairaWallet currently supports:

### Deposit

```text
Client
  ↓
Deposit Request
  ↓
Validate Idempotency Key
  ↓
Load Wallet
  ↓
Validate Wallet State
  ↓
Credit Wallet
  ↓
Create Transaction
  ↓
Create Ledger Entry
  ↓
Mark Transaction Successful
  ↓
Save Idempotency Key
  ↓
Response
```

### Withdrawal

Withdrawals validate the wallet state and available balance before modifying the wallet.

The operation records:

* Transaction reference
* Transaction type
* Amount
* Previous balance
* New balance
* Transaction status
* Ledger information

### Wallet Transfer

Transfers move funds atomically between two wallets.

The current flow is:

```text
Sender Wallet
      │
      ▼
   Debit
      │
      ▼
Receiver Wallet
      │
      ▼
   Credit
      │
      ▼
Create Transaction + Ledger Entries
```

The transfer operation is wrapped in a Spring transaction so that a failure during the operation causes the database transaction to roll back.

---

## 🔐 Idempotency

Financial APIs must protect against duplicate requests.

NairaWallet uses an idempotency key to prevent the same transaction request from being processed repeatedly.

For example:

```http
Idempotency-Key: transfer-12345
```

The service validates the key before processing the transaction and stores it after a successful operation.

This helps protect against scenarios such as:

```text
Client sends transfer
        ↓
Network timeout
        ↓
Client retries request
        ↓
Same Idempotency-Key
        ↓
Duplicate transaction prevented
```

The project is designed around the principle that **retrying a financial request should not accidentally create another financial effect**.

---

## 🔄 Transaction Management & Rollback

Financial operations use Spring's `@Transactional` support to maintain database consistency.

For example, if a transfer debits the sender but the receiver cannot accept the funds:

```text
Sender debit
     ↓
Receiver credit
     ↓
Receiver is INACTIVE
     ↓
Exception
     ↓
Transaction rollback
     ↓
Sender balance restored
```

This behavior is covered by a real Spring integration test against PostgreSQL.

This is important because unit tests with Mockito alone cannot prove that Spring actually rolls back a database transaction.

---

## 🔒 Optimistic Locking

Wallets use JPA's `@Version` field to support optimistic locking:

```java
@Version
private Long version;
```

This helps protect wallet balances from concurrent updates.

Conceptually:

```text
Request A ──┐
            ├── Wallet balance
Request B ──┘
```

Both requests may read the same wallet version, but only a valid version update can be committed.

The project also includes retry handling for optimistic locking failures.

---

## 📒 Ledger & Audit Trail

Financial transactions should be auditable rather than relying only on the current wallet balance.

NairaWallet therefore records ledger entries alongside wallet transactions.

A transaction records information such as:

```text
Transaction
├── Reference
├── Type
├── Amount
├── Status
└── Wallet
```

Ledger records capture:

```text
Ledger Entry
├── Wallet
├── Transaction
├── Amount
├── Balance Before
├── Balance After
├── CREDIT / DEBIT
└── Description
```

This provides a historical trail that can be used to understand how a wallet balance changed over time.

---

## 🧪 Testing

Testing is an important part of the current development process.

The project currently contains:

### Unit Tests

Mockito-based tests cover transaction service behavior including:

* Successful deposits
* Successful withdrawals
* Successful transfers
* Insufficient funds
* Inactive sender wallet
* Inactive receiver wallet
* Self-transfer rejection
* Transaction validation

### Integration Testing

A Spring Boot integration test uses the real PostgreSQL database to verify transactional rollback behavior.

The current test suite:

```text
Tests run: 11
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

This includes both service-level unit tests and Spring integration/context testing.

---

## 🗄️ Database

PostgreSQL is used as the primary relational database.

Current domain tables include:

```text
users
   │
   └── wallets
          │
          ├── wallet_transactions
          │
          └── ledger_entries

idempotency_keys
```

The database design uses constraints and relationships to help protect data integrity.

Examples include:

* Unique user email
* Unique user phone number
* Unique wallet per user
* Unique transaction references
* Wallet status constraints
* Optimistic locking version column

---

## 📡 API Endpoints

The application currently exposes REST APIs for core wallet operations.

Examples:

```http
POST /api/v1/users
```

Create a user and wallet.

```http
POST /api/v1/wallets/{walletId}/deposit
```

Deposit funds.

```http
POST /api/v1/wallets/{walletId}/withdraw
```

Withdraw funds.

Transfer operations use the sender and receiver wallet IDs and require an idempotency key.

Transaction history can also be retrieved with pagination support.

---

## 🧠 Engineering Concepts Demonstrated

NairaWallet is being used to demonstrate practical understanding of backend and fintech engineering concepts including:

* Java 17
* Spring Boot
* Spring Data JPA
* Hibernate
* PostgreSQL
* REST API design
* Layered architecture
* Domain-driven state changes
* Database transactions
* `@Transactional`
* Optimistic locking
* Transaction rollback
* Idempotency
* Financial transaction references
* Ledger-based auditing
* Exception handling
* Pagination
* Unit testing
* Integration testing
* Mockito
* Git/GitHub

---

## 📈 Current Development Status

The core wallet functionality is implemented and tested.

### Completed

* [x] User creation
* [x] Automatic wallet creation
* [x] Wallet balance management
* [x] Wallet activation/inactivation
* [x] Deposit
* [x] Withdrawal
* [x] Wallet transfer
* [x] Insufficient-funds validation
* [x] Self-transfer validation
* [x] Idempotency validation
* [x] Transaction records
* [x] Ledger entries
* [x] Optimistic locking
* [x] Global exception handling
* [x] Pagination for transaction history
* [x] Mockito unit tests
* [x] Spring integration testing
* [x] Transaction rollback testing

---

## 🔭 Roadmap

The project will continue to evolve toward a more production-oriented fintech backend.

Planned improvements include:

* [ ] Improve automated test coverage
* [ ] Repository/database integration tests
* [ ] Database migration management with Flyway
* [ ] Request validation improvements
* [ ] Authentication and authorization
* [ ] Redis-based capabilities
* [ ] Payment provider abstraction
* [ ] Payment gateway integration
* [ ] Webhook processing
* [ ] Reconciliation workflows
* [ ] Asynchronous transaction processing
* [ ] Rate limiting
* [ ] Circuit breaker patterns
* [ ] Structured observability and monitoring
* [ ] Dockerized deployment
* [ ] Production deployment

---

## 🎯 Why I Built NairaWallet

NairaWallet started as a way to strengthen my Java and Spring Boot backend skills through a realistic fintech project.

As the project evolved, the focus moved beyond simply making APIs work toward understanding the problems that matter when software handles money:

**What happens if a request is sent twice?**

**What happens if two requests modify the same wallet simultaneously?**

**What happens if one side of a transfer fails?**

**How can a transaction be audited later?**

**How do we prove that database rollback actually works?**

These questions have shaped the current architecture and development of NairaWallet.

The project is an ongoing demonstration of my approach to building **reliable, maintainable Java backend systems for financial applications**.
