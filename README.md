# NairaWallet 💳

NairaWallet is a **production-style Nigerian fintech e-wallet backend** built with Java and Spring Boot.

The project is currently **unfinished and actively under development**. However, the core implemented endpoints are functional and have been tested locally using Postman.

I am publishing the current state of the project to GitHub primarily to **preserve the codebase while transitioning to a new development laptop**, and I will continue development from this repository once the new environment is ready.

---

## 🚧 Project Status

**Status: Work in Progress**

The current implementation is not the final version of NairaWallet. Some production-level features, improvements, and refinements are still pending.

That said, the currently implemented functionality is working, including:

* User registration
* Automatic wallet creation
* Wallet retrieval
* Deposits
* Withdrawals
* Wallet-to-wallet transfers
* Transaction records
* Double-entry ledger entries
* Idempotency protection
* Optimistic locking
* Request validation
* Global exception handling
* Database migrations with Flyway

The API endpoints currently implemented have been tested successfully during development.

---

## 🎯 Project Goal

The goal of NairaWallet is to build a realistic backend for a Nigerian digital wallet while applying concepts commonly used in fintech systems, including:

* Transaction management
* Idempotency
* Concurrency control
* Optimistic locking
* Double-entry accounting
* Transaction ledgers
* Database consistency
* API validation
* Exception handling
* Database migrations
* Redis
* PostgreSQL
* Event-driven architecture
* Observability and scalability

The project is also being used as a practical learning project for understanding how **Java/Spring Boot backend systems can be designed for fintech use cases**.

---

## 🛠️ Technology Stack

* **Java 17**
* **Spring Boot**
* **Spring Data JPA / Hibernate**
* **Spring Validation**
* **PostgreSQL**
* **Redis**
* **Flyway**
* **Maven**
* **REST APIs**
* **Postman**
* **Git / GitHub**

---

## 🏗️ Current Architecture

The current project follows a layered Spring Boot architecture:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
PostgreSQL
```

The transaction flow also incorporates:

```text
Client Request
      ↓
Controller
      ↓
Validation
      ↓
Transaction Service
      ↓
Idempotency Check
      ↓
Wallet Validation
      ↓
Database Transaction
      ↓
Wallet Update
      ↓
Transaction Record
      ↓
Ledger Entries
      ↓
Response
```

---

## 💰 Wallet Operations

### Deposit

A deposit increases the wallet balance and creates the corresponding transaction and ledger records.

Idempotency protection is used to prevent the same request from being processed multiple times.

### Withdrawal

A withdrawal validates the wallet and available balance before debiting the wallet.

Optimistic locking is used to help prevent concurrent requests from incorrectly modifying the same wallet balance.

### Transfer

Wallet-to-wallet transfers debit the sender and credit the recipient within a database transaction.

The transfer also creates corresponding ledger entries to maintain an auditable record of the movement of funds.

---

## 🔐 Fintech Concepts Implemented

### Idempotency

The API supports idempotency keys for transaction requests.

This helps protect against duplicate transactions when a client retries a request because of network failures or timeouts.

Example:

```http
Idempotency-Key: unique-request-key
```

---

### Optimistic Locking

Wallets use JPA's optimistic locking mechanism:

```java
@Version
private Long version;
```

This helps protect wallet balances from lost updates when multiple requests attempt to modify the same wallet concurrently.

---

### Double-Entry Ledger

Financial movements are recorded using ledger entries rather than relying only on the wallet balance.

For example:

```text
Transfer ₦10,000

Sender Wallet
    DEBIT  ₦10,000

Recipient Wallet
    CREDIT ₦10,000
```

This provides an auditable representation of financial movements.

---

### Database Transactions

Financial operations use Spring's transaction management to ensure that related database operations succeed or fail together.

For example:

```java
@Transactional
```

A transfer should not result in the sender being debited while the recipient is not credited.

---

## 🗄️ Database

The project currently uses **PostgreSQL**.

Main entities include:

```text
users
wallets
wallet_transactions
ledger_entries
idempotency_keys
```

### Database Schema

The project is intended to use **Flyway** for database schema migrations as the project moves toward a more production-ready setup.

For the current development stage, however, Hibernate's `ddl-auto` is temporarily set to `update`, allowing Hibernate to create and update the required database tables automatically.

Flyway migrations will be used as the project progresses toward a more controlled and production-ready database migration strategy.

---

## 🐳 Running the Project

### Requirements

You will need:

* Java 17+
* Maven
* PostgreSQL
* Redis

---

## 🔗 API Endpoints

The currently implemented API includes endpoints for:

### Users

```http
POST /api/v1/users
```

Creates a user and automatically creates the associated wallet.

### Wallet

```http
GET /api/v1/wallets/{walletId}
```

Retrieves wallet information.

### Deposit

```http
POST /api/v1/transactions/deposit
```

Deposits funds into a wallet.

### Withdrawal

```http
POST /api/v1/transactions/withdraw
```

Withdraws funds from a wallet.

### Transfer

```http
POST /api/v1/transactions/transfer
```

Transfers funds between wallets.

> Endpoint names and request/response structures may change as the project continues to evolve.

---

## 🧪 Testing

The implemented endpoints have been tested locally using **Postman**.

Testing currently covers areas such as:

* Successful requests
* Validation failures
* Duplicate requests
* Insufficient wallet balance
* Invalid wallets
* Invalid transfers
* Concurrent wallet updates
* Transaction creation
* Ledger creation
* Exception handling

Automated unit and integration testing is still being expanded.

---

## 🚧 Planned Improvements

The project is intentionally incomplete. Future development will include improvements such as:

* More comprehensive unit and integration tests
* Mockito-based service testing
* Redis integration
* Kafka/event-driven processing
* Outbox pattern
* Payment provider integration
* Webhook processing
* Transaction reconciliation
* Rate limiting
* Circuit breakers
* Improved observability
* Metrics and monitoring
* Authentication and authorization improvements
* API documentation
* More robust audit capabilities
* Production deployment configuration
* Additional fintech business rules
* Docker and containerized development environment

---

## ⚠️ Important Disclaimer

NairaWallet is a **learning and portfolio project** and should not currently be considered production-ready financial software.

Although the implemented endpoints are functional, the project is still under development and has not undergone the level of security, compliance, performance, reliability, and operational testing required for a real financial institution.

The project is intended to demonstrate backend engineering concepts and serve as an evolving implementation of a fintech wallet system.

---

## 📌 Current Development Stage

This repository represents the **current development state of NairaWallet as of September 2026**.

Development will continue from this repository, with additional features and production-grade improvements being added over time.

**This is not the finished product — it is the foundation I'm building on.**

---

## 👨🏽‍💻 Author

**Yusuff Ibrahim Olawale**

Java Backend Developer | Spring Boot | Fintech Backend Engineering

GitHub: `BashorunDin0`

---

## ⭐ Why This Repository Exists

This repository is being maintained as both a **portfolio project and development backup**.

The current codebase is being pushed to GitHub so development can be safely recovered and continued when moving to a new development machine.

More features and improvements will be added as development continues.
