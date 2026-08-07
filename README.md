# PVR 3D Labs – Payment Microservice

Independent Spring Boot payment service for the PVR 3D Labs Angular e-commerce storefront.  
Integrates with **Cashfree Payment Gateway** on the server side only — client ID / secret never reach the browser.

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.x |
| Build | Maven |
| Payments | Cashfree PG Java SDK (`com.cashfree.pg.java:cashfree_pg`) |
| API docs | springdoc-openapi (Swagger UI) |
| Ops | Spring Boot Actuator |
| Validation | Jakarta Bean Validation |
| Misc | Lombok, Jackson |

## Architecture

```
com.pvrlabs.payment
├── controller      REST endpoints
├── service         Business orchestration
├── config          Cashfree, CORS, OpenAPI, properties
├── dto             Request / response contracts
├── exception       @ControllerAdvice + domain errors
├── integration     Future Order / Product / User hooks
└── util            Order ID + JSON helpers
```

Prepared for composition with future microservices via:

- `services.order.base-url`
- `services.product.base-url`
- `services.user.base-url`

Current publisher (`LoggingOrderEventPublisher`) logs intended outbound calls so you can swap in `RestClient` / messaging later without changing controllers.

## REST API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/payment/create-order` | Create Cashfree order → returns `paymentSessionId` |
| `GET` | `/api/payment/status/{orderId}` | Poll order / payment status |
| `POST` | `/api/payment/webhook` | Cashfree server-to-server webhook (signature verified) |

Swagger UI (dev): [http://localhost:8085/swagger-ui.html](http://localhost:8085/swagger-ui.html)  
Health: [http://localhost:8085/actuator/health](http://localhost:8085/actuator/health)

---

## Prerequisites

1. **JDK 21+**
2. **Maven 3.9+**
3. Cashfree merchant account (Sandbox keys for local testing)
4. Angular app running on `http://localhost:4200` (or update CORS)

---

## Maven dependencies (already in `pom.xml`)

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.4.5</version>
</parent>

<!-- Core -->
spring-boot-starter-web
spring-boot-starter-validation
spring-boot-starter-actuator

<!-- Cashfree -->
<dependency>
  <groupId>com.cashfree.pg.java</groupId>
  <artifactId>cashfree_pg</artifactId>
  <version>6.0.2</version>
</dependency>
<dependency>
  <groupId>com.squareup.okhttp3</groupId>
  <artifactId>okhttp</artifactId>
  <version>4.12.0</version>
</dependency>

<!-- Docs / DX -->
springdoc-openapi-starter-webmvc-ui
lombok
```

---

## Configuration (`application.yml`)

Credentials are loaded from **environment variables** (never hard-coded):

| Variable | Description | Example |
|---|---|---|
| `CASHFREE_CLIENT_ID` | App ID | `TEST123...` |
| `CASHFREE_CLIENT_SECRET` | Secret key | `cfsk_...` |
| `CASHFREE_ENVIRONMENT` | `SANDBOX` or `PRODUCTION` | `SANDBOX` |
| `CASHFREE_RETURN_URL` | Angular callback after checkout | `http://localhost:4200/payment/callback` |
| `CASHFREE_NOTIFY_URL` | Public webhook URL | `https://api.example.com/api/payment/webhook` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated Angular origins | `http://localhost:4200` |
| `SERVER_PORT` | Service port | `8085` |

Copy `.env.example` and export values (PowerShell):

```powershell
$env:CASHFREE_CLIENT_ID="your_app_id"
$env:CASHFREE_CLIENT_SECRET="your_secret"
$env:CASHFREE_ENVIRONMENT="SANDBOX"
$env:CASHFREE_RETURN_URL="http://localhost:4200/payment/callback"
$env:CASHFREE_NOTIFY_URL="http://localhost:8085/api/payment/webhook"
$env:CORS_ALLOWED_ORIGINS="http://localhost:4200"
```

Bash:

```bash
export CASHFREE_CLIENT_ID=your_app_id
export CASHFREE_CLIENT_SECRET=your_secret
export CASHFREE_ENVIRONMENT=SANDBOX
```

### Run

```bash
mvn clean spring-boot:run
```

Or package and run:

```bash
mvn clean package -DskipTests
java -jar target/payment-service-1.0.0-SNAPSHOT.jar
```

Production profile:

```bash
java -jar target/payment-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

---

## Sample requests

### Create order

```http
POST http://localhost:8085/api/payment/create-order
Content-Type: application/json

{
  "orderAmount": 1499.00,
  "orderCurrency": "INR",
  "orderNote": "Custom 3D figurine",
  "cartId": "CART-88",
  "userId": "USR-10042",
  "customerDetails": {
    "customerId": "USR-10042",
    "customerPhone": "9876543210",
    "customerEmail": "buyer@example.com",
    "customerName": "Asha Sharma"
  }
}
```

Response (safe for frontend — **no secrets**):

```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {
    "orderId": "PVR-ORD-20260806-ABC123",
    "paymentSessionId": "session_...",
    "orderAmount": 1499.00,
    "orderCurrency": "INR",
    "orderStatus": "ACTIVE",
    "environment": "SANDBOX"
  }
}
```

### Payment status

```http
GET http://localhost:8085/api/payment/status/PVR-ORD-20260806-ABC123
```

### Webhook

Configure the same URL in Cashfree dashboard. Headers required:

- `x-webhook-signature`
- `x-webhook-timestamp`

The service verifies HMAC-SHA256 with the **server-side** client secret before processing.

---

## Angular checkout integration

### 1. Install Cashfree JS SDK

```bash
npm install @cashfreepayments/cashfree-js
```

### 2. Environment

`src/environments/environment.ts`:

```ts
export const environment = {
  production: false,
  paymentApiBaseUrl: 'http://localhost:8085'
};
```

### 3. Payment API service

```ts
// payment-api.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';

export interface CreateOrderRequest {
  orderAmount: number;
  orderCurrency: string;
  orderNote?: string;
  cartId?: string;
  userId?: string;
  customerDetails: {
    customerId: string;
    customerPhone: string;
    customerEmail?: string;
    customerName?: string;
  };
}

export interface CreateOrderResponse {
  success: boolean;
  message: string;
  data: {
    orderId: string;
    paymentSessionId: string;
    orderAmount: number;
    orderCurrency: string;
    orderStatus: string;
    environment: 'SANDBOX' | 'PRODUCTION';
  };
}

export interface PaymentStatusResponse {
  success: boolean;
  data: {
    orderId: string;
    orderStatus: string;
    paymentStatus: string;
    orderAmount: number;
    orderCurrency: string;
  };
}

@Injectable({ providedIn: 'root' })
export class PaymentApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.paymentApiBaseUrl;

  createOrder(body: CreateOrderRequest): Observable<CreateOrderResponse> {
    return this.http.post<CreateOrderResponse>(`${this.base}/api/payment/create-order`, body);
  }

  getStatus(orderId: string): Observable<PaymentStatusResponse> {
    return this.http.get<PaymentStatusResponse>(`${this.base}/api/payment/status/${orderId}`);
  }
}
```

### 4. Checkout page flow

```ts
import { load } from '@cashfreepayments/cashfree-js';
import { PaymentApiService } from './payment-api.service';

async checkout() {
  // A) Create order on YOUR backend (secrets stay server-side)
  const res = await firstValueFrom(this.paymentApi.createOrder({
    orderAmount: this.cartTotal,
    orderCurrency: 'INR',
    cartId: this.cartId,
    userId: this.userId,
    customerDetails: {
      customerId: this.userId,
      customerPhone: this.phone,
      customerEmail: this.email,
      customerName: this.name
    }
  }));

  const { paymentSessionId, orderId, environment } = res.data;

  // Persist orderId for the callback / status poll page
  sessionStorage.setItem('pvr_order_id', orderId);

  // B) Open Cashfree checkout with paymentSessionId only
  const cashfree = await load({
    mode: environment === 'PRODUCTION' ? 'production' : 'sandbox'
  });

  await cashfree.checkout({
    paymentSessionId,
    redirectTarget: '_self'
  });
}
```

### 5. Callback / status page

After redirect to `/payment/callback?order_id=...`:

```ts
ngOnInit() {
  const orderId =
    this.route.snapshot.queryParamMap.get('order_id')
    ?? sessionStorage.getItem('pvr_order_id');

  if (!orderId) {
    this.error = 'Missing order id';
    return;
  }

  this.paymentApi.getStatus(orderId).subscribe({
    next: (res) => {
      this.status = res.data.paymentStatus || res.data.orderStatus;
      // SUCCESS / PAID → show confirmation; else show retry / failure UI
    },
    error: () => (this.error = 'Unable to verify payment')
  });
}
```

### Security rules for Angular

- Never put `CASHFREE_CLIENT_SECRET` (or App ID secret) in Angular env files.
- Frontend only needs `paymentSessionId` + environment mode (`sandbox` / `production`).
- Prefer webhook + status poll for fulfillment; do not trust client-only success events.

---

## Cashfree dashboard checklist

1. Create Sandbox app → copy App ID + Secret Key into env vars.
2. Set webhook URL to a publicly reachable `/api/payment/webhook` (ngrok for local).
3. Enable payment methods you need (UPI, cards, netbanking, etc.).
4. Switch `CASHFREE_ENVIRONMENT=PRODUCTION` only with production keys.

---

## Future microservice wiring

`OrderEventPublisher` is the seam:

| Event | Intended consumer |
|---|---|
| Order created | Order service – attach payment session to cart/order |
| Webhook success/failure | Order service – mark paid / failed; User service – notify; Product – inventory |

Replace `LoggingOrderEventPublisher` with an HTTP/messaging implementation when those services exist.

---

## License

See [LICENSE](LICENSE).
