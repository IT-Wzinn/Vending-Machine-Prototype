# PRODUCT SPECIFICATION — VENDING MACHINE PROTOTYPE

**Document type:** Product & Technical Specification  
**Version:** 1.0  
**Status:** Initial implementation specification  
**Project type:** University prototype — software, payment integration, and physical IoT machine  
**Primary language:** Java  
**Target implementation window:** 15–21 days  
**Development approach:** AI-assisted, incremental implementation  
**Primary requirement:** A real working vending machine with real payment verification and physical dispensing from all three slots.

---

## 1. Project Overview

### 1.1. Product name

**Vending Machine Prototype**

### 1.2. Product objective

Build a web-based vending machine system that allows customers to purchase products by scanning a permanent QR code attached to the machine.

Customers can browse products, select a product, create an order, pay by bank transfer using a dynamically generated payment QR code, and receive the selected physical product after the backend verifies the payment.

The system must communicate with an ESP32 controller over the Internet and activate the correct motor mechanism.

The machine has three independently controlled dispensing slots. Each slot must be able to dispense its assigned product.

### 1.3. Core customer journey

1. A customer scans the permanent QR code attached to the machine.
2. The customer's phone opens the public ordering website.
3. The customer browses the available products and prices.
4. The customer selects a product and confirms the order.
5. The backend creates a pending order and generates payment instructions.
6. The customer scans the payment QR code using a supported banking application.
7. The customer completes the bank transfer.
8. The payment provider or integration service sends a payment notification to the backend.
9. The backend validates the transaction and records the payment.
10. The backend creates a dispensing command for the correct slot.
11. The command is delivered to the ESP32 through MQTT.
12. The ESP32 activates the corresponding motor mechanism.
13. The ESP32 reports the dispensing result to the backend.
14. The customer sees the final order status on the website.

**Critical rule:** A successful browser redirect, a customer clicking a button, or a screenshot of a payment receipt must never be treated as proof of payment.

### 1.4. Project success definition

The project is successful only when all of the following work together:

- The permanent machine QR opens the public website.
- Customers can browse and order products from a phone.
- The backend creates and stores valid orders.
- A real bank transfer can be automatically verified.
- Duplicate payment notifications do not cause duplicate dispensing.
- A confirmed payment triggers the correct physical slot.
- All three slots can dispense their assigned products.
- The backend receives and records the ESP32 dispensing result.
- Customers can see the actual order status.
- The system handles payment failures, network interruptions, and uncertain dispensing outcomes safely.

A frontend-only simulation or a motor demonstration without verified payment does not satisfy the project requirements.

---

## 2. Scope and Priorities

### 2.1. Mandatory features — P0

| Feature | Requirement |
|---|---|
| Permanent machine QR | Opens the public ordering page for this machine |
| Product catalog | Shows products, prices, images or placeholders, availability |
| Product ordering | Creates an order through the backend |
| Dynamic payment QR | Contains the correct recipient and order-specific transfer information |
| Real payment verification | Automatically verifies real incoming transactions |
| Payment idempotency | One bank transaction must not trigger multiple dispensing commands |
| Three dispensing slots | Each slot must control its own physical motor mechanism |
| ESP32 communication | Backend sends commands and receives results through authenticated MQTT |
| Order status | Customer can track payment and dispensing progress |
| Inventory control | Prevents ordering products that are unavailable |
| Admin page | Allows basic product, price, stock, and availability management |
| Persistent database | Stores products, orders, payment transactions, commands, and results |
| Basic security | Server-side validation, protected administration, secret management |
| Error handling | Handles timeouts, disconnections, failed dispensing, and reconciliation |

### 2.2. Secondary features — P1

Implement only after all P0 requirements work:

- Basic order history in the admin interface.
- Simple machine health and connectivity status.
- Basic operational logs.
- A basic manual stock adjustment feature.
- Clear customer instructions for payment and dispensing.
- Basic sales totals or daily order counts.

### 2.3. Out of scope for the initial MVP

Do not implement these unless the mandatory features are already complete:

- Customer accounts or login.
- Customer loyalty programs.
- Coupons and promotions.
- Multiple vending machines.
- Multiple payment providers.
- AI-based product recommendations.
- Complex analytics dashboards.
- Mobile applications.
- Kubernetes.
- Redis.
- Microservice architecture.
- A complex enterprise inventory management system.

The system must remain a small, understandable application that one student can implement and demonstrate within 15–21 days.

---

## 3. Confirmed Decisions and Assumptions

### 3.1. Confirmed architectural decisions

| Area | Decision |
|---|---|
| Frontend | React + TypeScript + Vite |
| Styling | Tailwind CSS |
| Backend | Java 21 + Spring Boot 3.x |
| Build tool | Maven |
| API style | REST API with JSON |
| Database | PostgreSQL |
| Database migrations | Flyway |
| IoT controller | ESP32 |
| Firmware framework | Arduino framework for ESP32 |
| Device communication | MQTT over TLS |
| Containerization | Docker for backend and local development |
| Version control | Git and GitHub |
| Customer authentication | Not required |
| Admin authentication | Required |
| Payment | Real bank transfer with automated transaction verification |
| Machine configuration | One machine, three independent dispensing slots |
| Customer interface | Mobile-first, Vietnamese-language interface |

Use stable, compatible dependency versions. Do not arbitrarily upgrade the framework or introduce unnecessary dependencies.

### 3.2. Provisional infrastructure choices

These are preferred starting points, not guaranteed free services:

- **Frontend hosting:** Vercel.
- **PostgreSQL hosting:** Supabase.
- **MQTT broker:** EMQX Cloud or another suitable authenticated TLS-enabled broker.
- **Backend hosting:** A continuously running Java/Docker-compatible service.
- **Payment integration:** A Vietnamese bank-transfer webhook provider such as SePay, subject to current account eligibility, pricing, documentation, and real-payment testing.

Before finalizing external services, verify current free-tier limits, pricing, uptime characteristics, webhook support, account requirements, and applicable terms.

Do not assume that a service is free or suitable merely because a free tier exists.

The backend must not depend on a hosting environment that routinely sleeps if that would make payment webhooks or device commands unreliable during the demonstration.

### 3.3. Hardware assumptions requiring physical validation

The following information is not yet finalized:

- Exact motor model and operating voltage.
- Motor startup and stall current.
- Motor driver model.
- Spring dimensions and mechanical geometry.
- Product dimensions, weight, and packaging friction.
- Power supply specifications.
- Whether the mechanism needs a home/limit switch, encoder, or other position sensor.
- Whether a product-drop sensor can be installed.
- Whether the ESP32 can maintain reliable Internet connectivity during the demonstration.

The implementation must not invent hardware specifications.

Use a hardware abstraction layer and configurable motor parameters where practical. Do not finalize motor timing or driver selection until the actual mechanism has been tested.

---

## 4. System Architecture

### 4.1. High-level architecture

The system contains three main application layers and the physical dispensing mechanism.

```text
CUSTOMER PHONE
     |
     | Scan permanent QR
     v
REACT + TYPESCRIPT FRONTEND
     |
     | HTTPS REST API
     v
JAVA SPRING BOOT BACKEND
     |
     +--------------------+
     |                    |
     v                    v
POSTGRESQL           PAYMENT PROVIDER
     |                    |
     |                    | Authenticated webhook
     |                    v
     |              SPRING BOOT BACKEND
     |
     v
ORDER + PAYMENT + INVENTORY STATE
     |
     | Authenticated MQTT over TLS
     v
MQTT BROKER
     |
     v
ESP32 CONTROLLER
     |
     +----------+----------+
     |          |          |
     v          v          v
   SLOT 1     SLOT 2     SLOT 3
    MOTOR      MOTOR      MOTOR
     |          |          |
     v          v          v
 PRODUCT 1  PRODUCT 2  PRODUCT 3
```

The backend is the authoritative source for orders, prices, payment verification, inventory, and dispensing authorization.

The ESP32 controls physical motors but must not determine whether a payment is valid.

The frontend displays information and submits customer actions but must never be trusted to authorize a dispensing operation.

### 4.2. Responsibilities

**Frontend**

- Render the product catalog.
- Let customers select products and confirm orders.
- Display payment instructions and QR codes.
- Poll the backend for order status.
- Show dispensing progress and error messages.
- Provide a protected admin interface.

**Backend**

- Validate all customer requests.
- Calculate prices and totals using database values.
- Create and manage orders.
- Integrate with the payment provider.
- Validate payment notifications.
- Prevent duplicate transaction processing.
- Manage inventory and stock reservations.
- Publish dispensing commands.
- Track device acknowledgements and results.
- Expose REST endpoints.
- Protect administrative operations.

**Database**

- Persist product data.
- Persist orders and payment state.
- Record incoming payment transactions.
- Record dispensing commands and device results.
- Record stock changes and relevant audit events.

**MQTT broker**

- Relay commands between backend and ESP32.
- Require authentication and TLS.
- Enforce device-specific topic permissions where supported.

**ESP32 firmware**

- Subscribe to its assigned command topic.
- Validate incoming commands.
- Reject expired, malformed, or duplicate commands.
- Activate only the requested slot.
- Enforce motor timing and safety limits.
- Report command acceptance and completion or failure.
- Avoid uncontrolled motor activation after a reboot or reconnect.

---

## 5. Permanent QR and Public Website

### 5.1. Permanent machine QR

The machine QR code is static and must be printed or attached to the machine.

It points to a stable public URL, for example:

`https://<frontend-domain>/machine/VM001`

The exact production URL must be configured after deployment.

The QR must not contain payment credentials, API secrets, or a privileged backend endpoint.

### 5.2. Machine identification

The URL contains a public machine identifier such as `VM001`.

The backend validates that the machine exists and is active before accepting an order.

The machine identifier is not a secret and must not grant administrative access.

### 5.3. Invalid machine behavior

If the machine does not exist or is inactive, the frontend displays a clear message and does not allow checkout.

### 5.4. Customer language

The customer interface should use Vietnamese and simple wording suitable for first-time users.

---

## 6. Customer-Facing Features

### 6.1. Product catalog

Display:

- Product name.
- Product image or placeholder.
- Unit price in VND.
- Availability.
- Optional short description.
- Clear selection and checkout actions.

Prices must be formatted as Vietnamese đồng without floating-point currency calculations.

The frontend may cache catalog information for display, but the backend must validate current price and stock when creating an order.

### 6.2. Product selection

For the initial MVP, one order contains one product and one quantity.

The initial quantity is fixed at one item per order. The database and API may support quantity as a field, but the physical dispensing logic must not assume that arbitrary quantities can be safely dispensed in one motor cycle.

If quantity is later supported, each required unit must have an explicit dispensing plan and the system must prevent unsupported quantities.

### 6.3. Checkout

When a customer confirms a selection:

1. Frontend sends the machine ID, product ID, and quantity to the backend.
2. Backend loads the product and its current price from PostgreSQL.
3. Backend checks machine status and available stock.
4. Backend validates the requested quantity.
5. Backend creates a pending order with a unique order ID.
6. Backend reserves the required stock or otherwise prevents overselling.
7. Backend creates payment instructions using the selected payment integration.
8. Backend returns the order ID, payable amount, payment QR information, and expiration time.

The frontend must never submit a trusted price that the backend accepts without validation.

### 6.4. Payment page

Display:

- Order reference.
- Product name.
- Amount due.
- Payment QR image.
- Recipient information returned by the configured payment integration.
- Exact transfer reference.
- Payment expiration time.
- Instructions for scanning with a banking application.
- Current payment status.

The payment QR must be generated from trusted backend-provided payment instructions.

Do not place secret API keys or private payment-provider credentials in the QR or frontend code.

### 6.5. Payment waiting state

After presenting the QR, the frontend polls the backend for the order status.

Suggested initial polling interval: approximately 2–3 seconds, with a reasonable maximum waiting period and cleanup when the page is closed.

Do not create unlimited polling requests.

The frontend must not mark an order as paid based on:

- A customer clicking “I have paid”.
- A screenshot or uploaded receipt.
- A successful redirect.
- A client-side timer.
- A locally stored flag.
- A response fabricated in browser developer tools.

Only the backend's verified payment state can authorize dispensing.

### 6.6. Dispensing progress

Show meaningful states:

- Payment confirmed.
- Preparing the product.
- Dispensing in progress.
- Dispensing completed.
- Dispensing failed or requires assistance.

If the backend cannot determine whether the item actually dropped, the customer message must not falsely claim verified delivery.

### 6.7. Failure messages

Messages should be understandable and actionable.

Examples:

- Payment is still being verified.
- The payment has not been received.
- This product is temporarily unavailable.
- The machine is temporarily offline.
- Dispensing may have failed; please contact the operator.
- Your order is being checked. Do not pay again.

Do not expose stack traces, database errors, internal broker addresses, credentials, or other technical secrets.

---

## 7. Real Payment Integration

### 7.1. Payment requirement

The production demonstration must use a real supported bank-transfer payment flow with automated verification.

The exact provider must be confirmed before implementation is considered complete.

A provider adapter must be used so that payment-provider-specific code does not spread throughout the order service.

Example logical interface:

- `createPaymentInstructions(order)`
- `verifyWebhook(request)`
- `parsePaymentEvent(request)`
- `matchTransactionToOrder(event)`
- `recordVerifiedTransaction(event)`

The interface is conceptual. Antigravity should implement a clean Java abstraction appropriate to the chosen provider.

### 7.2. Payment QR

Each order must have a unique order reference.

The QR should encode the supported payment format provided by the selected integration, including the recipient account and exact amount where supported.

The transfer description must contain a unique reference that can be matched reliably to the order.

The backend must not rely only on the product name or amount to identify an order.

### 7.3. Webhook processing

The payment webhook endpoint must:

1. Receive the provider notification.
2. Verify authenticity using the provider's documented authentication mechanism.
3. Reject invalid or unauthorized notifications.
4. Parse the transaction according to the provider's documented schema.
5. Verify that the transaction represents a completed incoming payment.
6. Validate the receiving account where the provider exposes this information.
7. Validate transaction amount and transfer reference.
8. Detect duplicate provider transaction IDs.
9. Match the transaction to a valid pending order.
10. Record the transaction durably.
11. Atomically transition the order to the paid state.
12. Create a dispensing command only if the order has not already been dispatched.
13. Return the response required by the provider.

Implement webhook authentication according to the provider's current documentation. Do not invent signature headers, authentication algorithms, or payload fields.

If the provider does not support reliable authentication or sufficient transaction information, do not treat it as suitable for the real-payment requirement.

### 7.4. Duplicate and delayed notifications

Webhook delivery can be repeated or delayed.

The system must be idempotent.

A unique provider transaction ID should be protected by a database uniqueness constraint where available.

A duplicate webhook must not create a second dispensing command.

If a transaction cannot be matched confidently, record it for reconciliation rather than dispensing an arbitrary product.

### 7.5. Incorrect payments

The system must safely handle:

- Wrong amount.
- Unknown transfer reference.
- Duplicate transaction.
- Payment arriving after order expiration.
- Payment for a cancelled or expired order.
- Provider timeout.
- Invalid webhook authentication.
- A transaction that has already been processed.

An unmatched or late real payment must be recorded for manual reconciliation or handled by a documented provider-specific recovery process. Do not silently discard a real incoming transaction.

Do not automatically refund a payment unless a supported, verified refund flow is explicitly implemented.

### 7.6. Payment versus dispensing

Payment confirmation and physical dispensing are separate operations.

The backend should commit the verified payment state and dispensing intent durably before attempting to send the command.

Do not hold a database transaction open while waiting for a network call to the MQTT broker or ESP32.

Use a transactional outbox or another durable dispatch mechanism if feasible. For the MVP, a database-backed command queue with retry-safe processing is acceptable.

A simple in-memory queue is not sufficient as the only record of a paid order awaiting dispensing.

---

## 8. Order State Machine

### 8.1. Required order states

Use explicit, documented order states. Suggested values:

- `PENDING_PAYMENT`
- `PAID`
- `DISPENSING`
- `COMPLETED`
- `DISPENSE_FAILED`
- `NEEDS_RECONCILIATION`
- `EXPIRED`
- `CANCELLED`

These states must have documented transition rules.

### 8.2. Allowed transitions

| Current state | Event | Next state |
|---|---|---|
| PENDING_PAYMENT | Valid payment verified | PAID |
| PENDING_PAYMENT | Expiration with no payment | EXPIRED |
| PENDING_PAYMENT | Valid cancellation before payment | CANCELLED |
| PAID | Durable dispensing command accepted for dispatch | DISPENSING |
| DISPENSING | Reliable dispensing success reported | COMPLETED |
| DISPENSING | Definitive failure reported | DISPENSE_FAILED |
| DISPENSING | Outcome cannot be determined safely | NEEDS_RECONCILIATION |
| DISPENSE_FAILED | Operator begins documented recovery | State transition defined by recovery rules |
| NEEDS_RECONCILIATION | Operator resolves the case | Appropriate final state under audit |

A paid order must not be reset to `PENDING_PAYMENT`.

A duplicate webhook must not transition an already-paid order back to an earlier state.

Do not automatically resend a dispensing command if it is uncertain whether the motor already dispensed the item.

### 8.3. Payment and stock reservation

When an order is created, reserve one unit of stock or use an equivalent database-safe mechanism.

If payment expires without a verified transaction, release the reservation according to the expiration policy.

If a real payment arrives after expiration, record it and require reconciliation unless a safe, documented recovery process can fulfill the order.

Update stock only according to a consistent policy. A recommended approach is:

- Reserve stock when an order is created.
- Convert the reservation to a committed stock deduction when the order becomes paid and enters the dispensing workflow.
- Do not deduct stock twice if the same payment or dispensing event is processed repeatedly.
- Record failures and reconciliation cases for operator review.

The implementation must document how stock is restored when payment expires or when a dispensing failure is confirmed.

---

## 9. Inventory Management

### 9.1. Product model

Each product belongs to one physical dispensing slot for this MVP.

A product contains:

- Unique product ID.
- Product name.
- Description, optional.
- Image URL, optional.
- Price in VND.
- Assigned slot number.
- Stock quantity.
- Active status.
- Creation and update timestamps.

### 9.2. Slot mapping

Use slot numbers `1`, `2`, and `3`.

The backend must validate the mapping between product and slot.

A customer must not be able to specify an arbitrary slot number through a public request.

The backend determines the slot from the trusted product configuration.

### 9.3. Stock constraints

- Stock must never be negative.
- Only active and available products may be ordered.
- Product price and slot assignments can be changed only through authorized administration.
- Stock reservations and deductions must be concurrency-safe.
- The system must not oversell a product when multiple orders are created simultaneously.

### 9.4. Physical stock accuracy

Without a reliable stock sensor or automated item counter, the database cannot know the exact remaining physical quantity.

The initial implementation must state this limitation explicitly.

Stock should be updated from recorded dispensing events and can be corrected by an administrator.

Do not claim real-time physical stock measurement unless actual sensors support it.

---

## 10. Admin Interface

### 10.1. Authentication

The admin interface must require authentication.

For the MVP, a single administrator account is sufficient.

Use a secure password-hashing approach supported by Spring Security.

Never store plaintext passwords or commit credentials to Git.

Admin endpoints must verify authorization on the backend, even if the frontend hides the admin pages from customers.

### 10.2. Required admin features

- Log in and log out.
- List products.
- Create a product.
- Edit product name and description.
- Update price.
- Assign the product to a slot.
- Adjust stock.
- Enable or disable a product.
- View recent orders.
- View payment status.
- View dispensing status.
- View machine connectivity and last-seen time if available.
- Review failed or uncertain dispensing cases.

Do not provide an unrestricted “dispense any slot” operation in the public API.

If manual dispensing is added for testing or recovery, it must be protected, explicitly confirmed, logged, and separated from normal customer checkout. It must never bypass payment rules accidentally.

### 10.3. Admin audit

Record significant administrative actions, especially:

- Price changes.
- Stock changes.
- Product-to-slot mapping changes.
- Manual dispensing actions.
- Reconciliation decisions.

---

## 11. Backend Specification

### 11.1. Technology

- Java 21.
- Spring Boot 3.x.
- Maven.
- Spring Web.
- Spring Validation.
- Spring Data JPA or a similarly maintainable persistence approach.
- PostgreSQL driver.
- Flyway.
- Spring Security for admin authentication.
- A maintained MQTT client library compatible with the chosen broker.
- Spring Boot Actuator for health checks where appropriate.
- Docker.

Prefer a modular monolith rather than microservices.

### 11.2. Suggested package structure

```text
com.vendingmachine
├── config
├── common
├── product
│   ├── controller
│   ├── service
│   ├── repository
│   └── dto
├── order
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
├── payment
│   ├── controller
│   ├── service
│   ├── provider
│   ├── repository
│   └── dto
├── inventory
├── dispensing
│   ├── service
│   ├── mqtt
│   ├── repository
│   └── dto
├── device
├── admin
└── security
```

The exact package layout may be adjusted, but business logic must not be placed entirely inside controllers.

### 11.3. REST API conventions

- Use JSON for normal request and response bodies.
- Use HTTPS in deployed environments.
- Validate request bodies.
- Use appropriate HTTP status codes.
- Return consistent error responses.
- Never expose internal exception traces to customers.
- Use pagination for order lists if needed.
- Use UUIDs or similarly non-guessable public order identifiers.
- Do not expose database entities directly when a DTO is more appropriate.
- Do not put secret credentials in response payloads.

### 11.4. Required public endpoints

The exact paths can be refined, but the following capabilities are mandatory.

| Method | Example endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/machines/{machineId}` | Validate and retrieve public machine information |
| GET | `/api/v1/machines/{machineId}/products` | List available products |
| POST | `/api/v1/orders` | Create a customer order |
| GET | `/api/v1/orders/{orderId}` | Retrieve order status and payment instructions |
| GET | `/api/v1/orders/{orderId}/status` | Retrieve current order status |
| POST | `/api/v1/webhooks/payments/{provider}` | Receive payment-provider notifications |

The status endpoint may be combined with the order endpoint if the response contract remains clear.

Do not create a public endpoint that allows the customer to set an order to `PAID` or request arbitrary motor activation.

### 11.5. Required admin endpoints

A protected `/api/v1/admin` namespace should support:

- Product creation and editing.
- Product activation/deactivation.
- Stock adjustment.
- Order listing and detail views.
- Device status inspection.
- Dispensing failure and reconciliation inspection.
- Audited manual recovery actions if implemented.

All admin endpoints require backend authorization.

### 11.6. Validation and error responses

Validate:

- Machine existence and active state.
- Product existence and active state.
- Quantity.
- Available stock.
- Price from the database.
- Order ownership or public access token where appropriate.
- Payment transaction identifiers.
- Slot number and mapping.
- MQTT command format.
- Admin authorization.

For order status access, do not rely solely on sequential numeric IDs. Use UUIDs or another appropriate identifier, and consider a separate random public access token if additional privacy is needed.

---

## 12. Database Specification

Use PostgreSQL with Flyway migrations.

All monetary amounts should use a precise representation such as integer VND or an appropriate decimal database type. Do not use floating-point values for currency.

### 12.1. `machines`

Suggested fields:

- `id`
- `machine_code`
- `name`
- `status`
- `created_at`
- `updated_at`

For the initial MVP, create one machine record with code `VM001`.

### 12.2. `products`

Suggested fields:

- `id`
- `name`
- `description`
- `image_url`
- `price_vnd`
- `slot_number`
- `stock_quantity`
- `reserved_quantity`, if reservation accounting is used
- `is_active`
- `created_at`
- `updated_at`

Constraints:

- `price_vnd > 0`
- `slot_number` is one of 1, 2, or 3.
- Stock quantities cannot be negative.
- Product-slot assignment must follow the one-product-per-slot MVP rule.

### 12.3. `orders`

Suggested fields:

- `id`
- `public_order_code`
- `machine_id`
- `product_id`
- `quantity`
- `unit_price_vnd`
- `total_amount_vnd`
- `transfer_reference`
- `status`
- `payment_expires_at`
- `paid_at`
- `dispensing_started_at`
- `completed_at`
- `created_at`
- `updated_at`

Store the price snapshot at order creation so later product price changes do not change an existing order.

### 12.4. `payment_transactions`

Suggested fields:

- `id`
- `provider`
- `provider_transaction_id`
- `order_id`, nullable when an incoming transaction cannot be matched
- `amount_vnd`
- `currency`
- `direction`
- `transaction_status`
- `transfer_reference`
- `received_at`
- `processed_at`
- `raw_payload`, subject to data minimization
- `verification_status`
- `created_at`

Constraints:

- Unique provider transaction ID within the correct provider scope.
- Only verified, eligible incoming transactions can mark an order as paid.
- Unmatched transactions must remain visible for reconciliation.

Sensitive provider information must be redacted or excluded from stored raw payloads where appropriate.

### 12.5. `dispense_commands`

Suggested fields:

- `id`
- `command_id`
- `order_id`
- `machine_id`
- `slot_number`
- `status`
- `attempt_count`
- `created_at`
- `published_at`
- `acknowledged_at`
- `finished_at`
- `failure_code`
- `failure_message`

Use a unique command identifier and prevent multiple active dispensing commands for the same order.

### 12.6. `device_events`

Suggested fields:

- `id`
- `machine_id`
- `command_id`, nullable
- `event_type`
- `slot_number`, nullable
- `payload`, after validation and redaction
- `created_at`

Use this table or an equivalent logging mechanism for device acknowledgements, dispensing results, heartbeat events, and relevant failures.

### 12.7. `inventory_movements`

Suggested fields:

- `id`
- `product_id`
- `order_id`, nullable
- `movement_type`
- `quantity_delta`
- `reason`
- `created_at`
- `created_by`, nullable for automated events

This table provides an auditable record of stock changes.

### 12.8. Admin account storage

Use Spring Security-compatible password hashing and appropriate database constraints.

Do not store plaintext passwords.

### 12.9. Database integrity

Use:

- Foreign keys.
- Unique constraints.
- Check constraints where appropriate.
- Indexes on frequently queried order codes, statuses, transaction identifiers, and timestamps.
- Database transactions for state changes that must be atomic.
- Flyway versioned migrations.

Do not depend on frontend validation for database integrity.

---

## 13. MQTT Communication Protocol

### 13.1. General requirements

Use MQTT over TLS.

The backend and ESP32 must authenticate to the broker using supported credentials.

Restrict topics so a device cannot subscribe to or publish arbitrary topics belonging to other devices.

Use a unique machine identifier, such as `VM001`.

### 13.2. Topic convention

Suggested topics:

```text
vending/VM001/commands
vending/VM001/events
vending/VM001/heartbeat
```

The exact topic ACLs must be configured in the broker.

Recommended permissions:

- Backend: publish commands and subscribe to device events and heartbeat.
- ESP32 for VM001: subscribe to VM001 commands and publish VM001 events and heartbeat.
- No anonymous access.
- No wildcard permissions unless necessary and explicitly restricted.

### 13.3. Command payload

Example logical payload:

```json
{
  "commandId": "UUID",
  "machineId": "VM001",
  "orderId": "ORDER_UUID",
  "slotNumber": 2,
  "action": "DISPENSE",
  "createdAt": "ISO-8601 timestamp",
  "expiresAt": "ISO-8601 timestamp",
  "parameters": {
    "motorRunMs": 800
  }
}
```

This is a schema example, not a finalized motor configuration.

Motor parameters must be validated against the actual mechanism and must not be accepted as arbitrary customer-controlled values.

### 13.4. Device acknowledgement

The ESP32 should report that it has accepted or rejected the command before or while beginning physical execution.

Example:

```json
{
  "eventId": "UUID",
  "commandId": "UUID",
  "machineId": "VM001",
  "eventType": "COMMAND_ACCEPTED",
  "slotNumber": 2,
  "timestamp": "ISO-8601 timestamp"
}
```

### 13.5. Dispensing result

Example:

```json
{
  "eventId": "UUID",
  "commandId": "UUID",
  "machineId": "VM001",
  "eventType": "DISPENSE_RESULT",
  "slotNumber": 2,
  "result": "SUCCESS",
  "verificationLevel": "MOTOR_CYCLE_ONLY",
  "failureCode": null,
  "timestamp": "ISO-8601 timestamp"
}
```

`MOTOR_CYCLE_ONLY` means the motor cycle finished according to the firmware, but the system has not independently confirmed that the product physically dropped.

If a drop sensor is installed and its readings are reliable, the event may report a stronger verification level.

The backend must not interpret a motor-cycle result as guaranteed product delivery.

### 13.6. Heartbeat

The ESP32 periodically publishes a heartbeat containing:

- Machine ID.
- Firmware version.
- Uptime or equivalent diagnostic data.
- Connection status where appropriate.
- Motor/controller health information where available.
- Timestamp or sequence information.

The backend records the last-seen time.

A heartbeat proves communication, not that every motor is mechanically functional.

### 13.7. Command reliability

Every command must have a unique `commandId`.

The ESP32 should maintain a bounded persistent record of recently processed command IDs, using a suitable persistence mechanism if feasible.

A duplicate command must not activate the motor a second time.

Use command expiration and stale-command rejection.

Avoid retained MQTT commands for dispensing operations.

If the broker disconnects or the ESP32 reboots, an expired command must not trigger dispensing later.

A command timeout must not automatically authorize blind retransmission of the motor action. If the outcome is uncertain, transition the order to `NEEDS_RECONCILIATION`.

### 13.8. Delivery semantics

MQTT delivery and physical motor execution are not the same thing.

The system must explicitly handle the case where:

1. ESP32 receives a command.
2. The motor begins or completes dispensing.
3. The acknowledgement is lost.
4. The backend times out.

The backend must not assume that the command never executed.

Exactly-once physical dispensing cannot be guaranteed by MQTT alone. Use idempotent command processing, persistent command state, acknowledgements, and reconciliation.

---

## 14. ESP32 Firmware Specification

### 14.1. Responsibilities

The ESP32 must:

- Connect to Wi-Fi.
- Connect securely to the configured MQTT broker.
- Authenticate to the broker.
- Subscribe to its assigned command topic.
- Validate command payloads.
- Reject malformed, unauthorized, expired, or duplicate commands.
- Map slot numbers 1–3 to the corresponding hardware output.
- Activate only the requested motor.
- Enforce motor timing limits.
- Stop the motor after the configured limit.
- Monitor available safety inputs.
- Publish acknowledgement and result events.
- Publish periodic heartbeat messages.
- Recover from network disconnections without uncontrolled motor activation.

### 14.2. Motor-control abstraction

Implement a slot-control abstraction so the firmware can control three independent slots without duplicating the full command-processing logic.

Conceptually:

- `dispenseSlot(1)`
- `dispenseSlot(2)`
- `dispenseSlot(3)`

These are conceptual operations. The actual implementation must use the selected motor driver and mechanism.

Each slot must have its own independent configuration.

### 14.3. Hardware constraints

- Never drive a motor directly from an ESP32 GPIO pin.
- Use a suitable motor driver or switching stage rated for the motor's voltage and startup/stall current.
- Use an appropriately rated power supply.
- Ensure the motor supply and ESP32 supply are electrically compatible.
- Establish a suitable common reference where required by the selected driver topology.
- Use appropriate fuses or current protection.
- Prevent motor inductive transients from damaging the electronics.
- Keep motor current away from the ESP32 power path as required by the electrical design.
- Confirm driver, wiring, and supply ratings before powering the mechanism.
- Do not use unverified pin assignments or guessed electrical ratings in the final hardware design.

### 14.4. Position and drop sensing

A home/limit switch or equivalent position feedback should be considered if the mechanism needs repeatable motion.

A product-drop sensor is recommended if feasible because it helps distinguish a completed motor cycle from an actual product drop.

If sensors cannot be installed, the system must explicitly report that success is based on motor-cycle completion only.

### 14.5. Firmware safety

The firmware must:

- Start with all motors off.
- Keep motors off during Wi-Fi or MQTT reconnection.
- Reject invalid slot numbers.
- Reject expired commands.
- Prevent concurrent motor operation unless the hardware is explicitly designed and tested for it.
- Enforce a maximum motor runtime.
- Stop on supported fault conditions.
- Avoid blocking network handling for long periods during motor operation.
- Keep credentials out of source control.
- Avoid logging Wi-Fi passwords, broker passwords, or secrets.

### 14.6. Motor selection policy

The exact motor and driver must be selected only after reviewing:

- Spring and shaft dimensions.
- Product size and weight.
- Required torque.
- Mechanism friction.
- Desired rotation and stopping behavior.
- Operating voltage and current.
- Available power supply.
- Mechanical mounting and coupling.

A geared DC motor with appropriate position feedback may be suitable. A stepper motor may be preferable for a mechanism requiring controlled rotation. Neither should be selected solely from the fact that the system uses ESP32.

The final choice must be based on physical testing.

---

## 15. Frontend Specification

### 15.1. Technology

- React.
- TypeScript.
- Vite.
- Tailwind CSS.
- A maintained QR display/generation library compatible with the chosen payment provider.
- Fetch or a lightweight HTTP client for API communication.

Do not introduce a large state-management framework unless the actual implementation requires it.

### 15.2. Public pages

**Machine/product page**

- Machine validation.
- Product list.
- Product availability.
- Product selection.
- Clear checkout action.

**Checkout/payment page**

- Order summary.
- Amount due.
- Payment QR.
- Transfer reference.
- Expiration countdown.
- Payment status polling.
- Clear instructions.

**Order status page**

- Payment state.
- Dispensing state.
- Success, failure, or reconciliation message.
- Order reference.

### 15.3. Admin pages

- Admin login.
- Product management.
- Stock management.
- Recent order list.
- Order detail.
- Dispensing failure/reconciliation view.
- Device status view.

### 15.4. Frontend quality requirements

- Mobile-first design.
- Vietnamese user-facing text.
- Loading states.
- Empty states.
- Validation messages.
- Network error handling.
- Disabled actions when appropriate.
- No secret keys in frontend environment variables.
- No direct database access from the customer frontend.
- No direct MQTT connection from the customer frontend.
- No authority to mark orders as paid or issue physical dispensing commands.

### 15.5. Frontend environment configuration

Use public environment variables only for non-secret values, such as the backend base URL.

Never expose:

- Database passwords.
- Payment-provider API secrets.
- Webhook secrets.
- MQTT credentials.
- Admin passwords.
- Private service-role keys.

---

## 16. Docker and Deployment

### 16.1. Local development

Use Docker Compose for a reproducible local environment.

The local environment should support:

- Java backend.
- PostgreSQL.
- Database initialization/migrations.
- Environment-based configuration.

A local MQTT broker may be included for development if useful.

The firmware toolchain may run outside Docker if that is simpler for the selected ESP32 development setup.

### 16.2. Production deployment

Deploy the frontend, backend, database, and MQTT broker using their respective suitable hosting environments.

Requirements:

- Frontend uses HTTPS.
- Backend uses HTTPS.
- Database connections use TLS where supported and appropriate.
- MQTT uses TLS.
- Production secrets are injected through the hosting environment.
- Database migrations are controlled.
- Health checks are available.
- Logs are available for debugging.
- The backend runs continuously enough to process payment webhooks and device commands reliably.

### 16.3. Docker requirements

Provide:

- A production Dockerfile for the Spring Boot backend.
- A local Docker Compose file.
- A `.env.example` with placeholder values only.
- A `.dockerignore`.
- A README section for local setup.
- A README section for deployment configuration.

Use a non-root runtime user where practical.

Do not bake credentials into Docker images.

Do not commit `.env` files containing real secrets.

### 16.4. Hosting constraints

Before selecting the final backend host, verify:

- Continuous process availability.
- Docker support.
- Inbound HTTPS access for webhooks.
- Outbound MQTT connectivity.
- Database network access.
- Cold-start and sleep behavior.
- Free-tier quotas and current prices.
- Resource limits.
- Logs and restart behavior.

A free hosting service that sleeps or cannot reliably receive webhooks is not acceptable as the final demonstration environment unless the limitations are explicitly mitigated and tested.

### 16.5. Deployment configuration

Use environment variables or the hosting platform's secret manager for settings such as:

```text
SPRING_PROFILES_ACTIVE
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
PAYMENT_PROVIDER
PAYMENT_API_KEY
PAYMENT_WEBHOOK_SECRET
MQTT_BROKER_URL
MQTT_USERNAME
MQTT_PASSWORD
MQTT_CLIENT_ID
MACHINE_ID
FRONTEND_ORIGIN
ADMIN_INITIAL_PASSWORD
```

These are illustrative variable names. Implement the variables actually required by the selected libraries and providers.

Do not put actual secret values in this specification, repository, logs, frontend build, or screenshots.

---

## 17. Security Requirements

### 17.1. Backend security

- Validate all incoming requests.
- Apply authentication and authorization to admin operations.
- Verify payment webhook authenticity.
- Prevent SQL injection through parameterized persistence operations.
- Configure CORS to allow only required origins in production.
- Apply rate limits or equivalent protections to sensitive public endpoints where practical.
- Avoid exposing internal errors.
- Protect order access against enumeration and unauthorized disclosure.
- Avoid logging sensitive payment data.
- Never trust customer-submitted prices, slot numbers, or payment status.

### 17.2. IoT security

- Use TLS for MQTT.
- Use unique, restricted credentials where supported.
- Restrict topic permissions.
- Validate all command fields.
- Reject expired commands.
- Prevent duplicate command execution.
- Keep credentials out of public repositories.
- Do not accept physical dispensing requests directly from the public frontend.

### 17.3. Payment security

- Validate transactions on the backend.
- Use the provider's documented webhook authentication mechanism.
- Use transaction IDs and order references for idempotency.
- Prevent replayed webhook events from triggering repeated dispensing.
- Record unmatched transactions for reconciliation.
- Do not accept screenshots as verification.
- Do not expose payment secrets to customers.

### 17.4. Secrets and repository hygiene

The repository must include a `.gitignore` covering environment files, generated builds, local credentials, and relevant IDE artifacts.

Provide placeholder configuration in `.env.example`.

Do not commit real credentials, personal bank account details, or production payment secrets.

---

## 18. Logging, Monitoring, and Recovery

### 18.1. Required logs

Record relevant events such as:

- Order created.
- Payment webhook received.
- Payment verified or rejected.
- Duplicate transaction detected.
- Stock reserved or updated.
- Dispensing command created.
- Command published.
- Device acknowledgement received.
- Motor result received.
- Device disconnected.
- Dispensing timeout.
- Reconciliation required.
- Admin changes.

Include timestamps and relevant IDs, such as order ID, transaction ID, command ID, and machine ID.

Do not log passwords, private keys, complete authentication headers, or unnecessary sensitive payment payloads.

### 18.2. Health monitoring

Provide an appropriate backend health endpoint.

Track device last-seen time using heartbeat events.

Distinguish between:

- Backend is running.
- Database is reachable.
- MQTT broker is reachable.
- ESP32 has recently communicated.
- A motor operation has completed.
- A product drop has been independently verified.

These are different health conditions and must not be conflated.

### 18.3. Recovery behavior

If the backend restarts, it must be able to recover pending paid orders and dispensing commands from persistent storage.

If MQTT disconnects, the backend should retain a durable command record and apply safe dispatch rules after reconnection.

If a command's physical outcome is uncertain, require reconciliation rather than blindly repeating it.

If the ESP32 reboots, motors must remain off until a new valid command is received.

---

## 19. Performance and Reliability

This is a small prototype, not a high-scale production vending platform.

The architecture should prioritize correctness, simplicity, and debuggability.

Requirements:

- Catalog requests should return promptly under normal prototype conditions.
- Order creation must be transactionally safe.
- Payment processing must be idempotent.
- Device commands must have a durable record.
- A slow frontend must not block payment processing.
- Long motor operations must not block the entire firmware networking loop.
- Temporary network failures must not cause duplicate dispensing.
- The application must provide useful error states instead of hanging indefinitely.

No unsupported throughput or latency guarantees should be claimed.

---

## 20. Testing and Acceptance Criteria

### 20.1. Frontend tests

Verify:

- Product catalog loads.
- Unavailable products cannot be purchased.
- Checkout submits the correct product ID and quantity.
- The payment page shows the backend-provided amount and reference.
- The UI reacts to order status changes.
- Network failures display actionable messages.
- Customers cannot mark their own orders as paid.
- Admin pages require authentication.

### 20.2. Backend tests

Verify:

- Product lookup and order creation.
- Server-side price calculation.
- Stock reservation.
- Invalid product and machine rejection.
- Order expiration.
- Payment verification.
- Duplicate webhook handling.
- Duplicate transaction rejection.
- Duplicate dispensing prevention.
- Command persistence and dispatch.
- Unauthorized admin requests are rejected.
- Database migrations run successfully.

### 20.3. Payment integration tests

Test the provider's sandbox or documented test environment if available.

Then verify the actual real-bank-transfer flow using an authorized account and a small controlled payment.

Acceptance criteria:

- A real valid incoming payment is automatically detected.
- A payment is matched to the correct order.
- The paid state is recorded in PostgreSQL.
- Invalid webhook authentication is rejected.
- Wrong amounts are not accepted as valid full payments.
- Duplicate notifications do not trigger duplicate dispensing.
- Unmatched and late transactions remain available for reconciliation.

The test environment must never be confused with production payment verification.

### 20.4. IoT tests

Test each slot independently:

- Slot 1 activates only motor 1.
- Slot 2 activates only motor 2.
- Slot 3 activates only motor 3.
- Each mechanism dispenses the assigned physical product.
- Motors stop within configured safety limits.
- Invalid slot numbers are rejected.
- Duplicate command IDs do not cause duplicate operation.
- Expired commands are rejected.
- A Wi-Fi or MQTT disconnection does not start an old command after reconnect.
- A reboot leaves all motors off.
- Device results reach the backend when connectivity is available.

### 20.5. End-to-end acceptance test

Perform a complete real transaction:

1. Scan the permanent QR code.
2. Select the first product.
3. Create an order.
4. Scan the payment QR using a supported banking application.
5. Complete the bank transfer.
6. Verify the payment automatically.
7. Confirm the correct command is dispatched.
8. Confirm the correct physical product is dispensed.
9. Confirm the backend records the result.
10. Confirm the customer sees the final state.

Repeat the complete flow for slots 2 and 3.

### 20.6. Fault-injection tests

Test:

- Payment notification arrives twice.
- Payment notification arrives late.
- ESP32 is offline after payment.
- MQTT connection drops during dispensing.
- Device acknowledgement is lost.
- Motor fails to complete its cycle.
- Product-drop sensor, if installed, reports no drop.
- Backend restarts with a paid order awaiting dispatch.
- A product becomes unavailable during checkout.

The system must avoid uncontrolled repeated motor activation and preserve enough information for recovery.

---

## 21. Implementation Constraints for Antigravity

Treat this document as the product requirements and architecture baseline.

### 21.1. Do not silently change requirements

Do not replace:

- Java Spring Boot with a different backend language.
- PostgreSQL with a different database.
- MQTT with direct browser-to-ESP32 control.
- Real payment verification with a simulated payment button.
- Three independent physical slots with a single simulated motor.
- Persistent order tracking with frontend-only state.

If a requirement cannot be implemented as written, explain the constraint and propose an explicit alternative before changing the design.

### 21.2. Avoid unnecessary complexity

Do not introduce microservices, Kubernetes, Redis, distributed tracing infrastructure, or complicated state-management libraries unless a concrete requirement justifies them.

Prefer a modular monolith, a clear database model, explicit state transitions, and well-defined service boundaries.

### 21.3. Do not fabricate external integrations

Do not invent:

- Payment provider endpoints.
- Webhook payload fields.
- Authentication headers.
- MQTT broker credentials.
- Hosting environment variables that a provider does not support.
- Motor specifications.
- GPIO assignments.
- Sensor behavior.

Use official documentation for the selected providers and libraries.

If a provider's current API or account requirements cannot be verified, mark the integration as blocked pending verification rather than pretending it is complete.

### 21.4. Work incrementally

Implement and test each component before integrating it with the next component.

Keep the application runnable as development progresses.

Do not generate the entire project in one unreviewed code dump.

Do not delete working code or replace the architecture without explaining why.

Use Git commits at meaningful milestones.

### 21.5. Required implementation documentation

The project should ultimately contain:

```text
README.md
PRODUCT_SPEC.md
.env.example
.gitignore
docker-compose.yml
backend/
frontend/
firmware/
docs/
```

The exact structure may be adapted if justified.

The implementation documentation should include:

- Local setup.
- Environment configuration.
- Database migrations.
- API endpoint reference.
- Payment provider configuration.
- MQTT topics and payloads.
- ESP32 flashing and configuration instructions.
- Hardware wiring documentation.
- Deployment instructions.
- Testing instructions.
- Known limitations and recovery procedures.

---

## 22. Outstanding Decisions That Require Verification

These items must be resolved before the relevant implementation is considered final.

### 22.1. Physical mechanism

Confirm:

- Exact spring and shaft geometry.
- Motor and driver selection.
- Motor power supply.
- Product dimensions and weight.
- Mechanical mounting.
- Position feedback requirements.
- Whether a drop sensor can be installed.

Do not invent final motor timing or electrical specifications.

### 22.2. Payment provider

Confirm:

- Provider eligibility and account requirements.
- Current pricing and applicable fees.
- Real-time webhook availability.
- Webhook authentication mechanism.
- Transaction fields needed to identify and validate a payment.
- Payment QR format.
- Availability of a sandbox or test environment.
- Actual incoming-payment behavior.

### 22.3. Deployment services

Confirm current service limits, pricing, persistent availability, network access, and deployment requirements for the chosen frontend host, backend host, database host, and MQTT broker.

### 22.4. Internet connectivity

Confirm how the ESP32 will connect during the physical demonstration, such as a phone hotspot or another Wi-Fi network.

### 22.5. Product-drop verification

Determine whether a physical sensor can verify that an item actually exits the machine.

If not, the system must report motor-cycle completion rather than guaranteed product delivery.

---

## 23. Definition of Done

The project is considered ready for its final demonstration only when:

- [ ] The public website is deployed and accessible by the permanent machine QR.
- [ ] Customers can browse products and create orders.
- [ ] Prices and stock are validated by the backend.
- [ ] The payment QR is generated from valid order payment instructions.
- [ ] Real incoming bank transfers are automatically verified.
- [ ] Duplicate payment notifications cannot trigger duplicate dispensing.
- [ ] Orders and payment transactions persist in PostgreSQL.
- [ ] Paid orders create durable dispensing commands.
- [ ] MQTT communication is authenticated and encrypted in deployment.
- [ ] The ESP32 receives and validates commands.
- [ ] All three independent slots physically dispense their assigned products.
- [ ] The backend records the device's reported outcome.
- [ ] The customer can see the order's current status.
- [ ] The admin can manage products and stock through protected endpoints.
- [ ] Secrets are not committed to the repository or exposed to the frontend.
- [ ] Docker-based local setup and deployment instructions are documented.
- [ ] Duplicate events, timeouts, network failures, and uncertain outcomes have been tested.
- [ ] The system does not claim confirmed product delivery unless it has adequate physical evidence.

**Final priority:** Correct payment verification, safe and reliable physical dispensing, and a complete end-to-end demonstration take precedence over visual polish and optional features.
