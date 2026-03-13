# Event-Driven Order Fulfillment Service

A robust backend microservice designed for asynchronous order fulfillment, leveraging an event-driven architecture with RabbitMQ and MySQL.

## Features
- Consumes `order.placed` events from RabbitMQ.
- Uses idempotency checks to prevent duplicate order processing.
- Updates order status transactionally in MySQL (PENDING -> PROCESSING -> PROCESSED).
- Publishes `order.processed` events upon successful processing.
- Implements Dead Letter Queues (DLQ) for permanent failures.
- Auto-retries for transient errors using Spring AMQP's built-in retry mechanism.
- Provides a `/health` endpoint for monitoring.
- Fully containerized with Docker and Docker Compose.

## Prerequisites
- Docker and Docker Compose
- Java 17 and Maven (for local development, optional if only using Docker)

## Project Structure
- `src/main/java`: Source code of the application.
- `src/main/resources/application.yml`: Configuration file.
- `src/test`: Unit and Integration tests.
- `docker-compose.yml`: Orchestrates the app, MySQL, and RabbitMQ.
- `db_init/init.sql`: Seed data and schema initialization.

## Environment Variables
Ensure you have an `.env` file based on `.env.example` if running locally, or configure the environment variables within `docker-compose.yml`. Key variables include:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_RABBITMQ_HOST`
- `SPRING_RABBITMQ_USERNAME`
- `SPRING_RABBITMQ_PASSWORD`

## How to Run
1. Make sure you don't have existing services running on ports `8080`, `3306`, `5672`, or `15672`.
2. Run the full stack with Docker Compose:
   ```bash
   docker-compose up -d --build
   ```
3. Check the health endpoint: `http://localhost:8080/health`
4. Access RabbitMQ Management UI: `http://localhost:15672` (guest/guest)

## Event Flow & Testing
1. Navigate to the RabbitMQ Management UI.
2. Go to the **Exchanges** tab and select `order.events`.
3. Under **Publish message**, insert routing key: `order.placed`.
4. Payload:
   ```json
   {
     "orderId": "order789",
     "productId": "prodC",
     "quantity": 3,
     "customerId": "custZ",
     "timestamp": "2023-10-27T10:00:00Z"
   }
   ```
5. Click **Publish message**.
6. The service will consume it, update MySQL, and publish a new message to `order.events` with routing key `order.processed`. You can view it by binding a test queue to `order.events` with `order.processed`.

## Running Tests
Run unit and integration tests (requires Docker daemon running for Testcontainers):
```bash
mvn clean test
```

## Error Handling
- Transient errors (e.g., db connection issues) trigger automatic retries (up to 3 times) before being gracefully sent to the DLQ (`order.dlq`).
- Permanent errors (e.g., malformed payloads) are rejected immediately and sent straight to the DLQ.

## Idempotency Strategy
The `OrderProcessingService` checks the DB for an existing `orderId` before proceeding. If the order is already in `PROCESSING` or `PROCESSED` state, it returns early and ACKs the message, avoiding unintended side-effects.