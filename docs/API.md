# API and Postman walkthrough

Import `postman/product-service.postman_collection.json`. Set `baseUrl`. All bodies are JSON and no authentication header is required.

Examples below assume product ID 1001. The collection captures real generated IDs. Its sequential lifecycle uses 100 initial units, adds 50, releases one order, and confirms another. The smaller examples below use separate 100-unit starting scenarios to explain each operation.

| Method and path | Access | Success |
|---|---|---|
| POST /api/products | public | 201 with Location header |
| GET /api/products/{productId} | public | 200 |
| GET /api/products?page=0&size=10&sort=price,desc | public | 200 |
| GET /api/products/search?keyword=samsung&page=0&size=10 | public | 200 |
| PUT /api/products/{productId} | public | 200 |
| DELETE /api/products/{productId} | public | 204 |
| GET /internal/api/inventory/{productId} | public | 200 |
| PATCH /internal/api/inventory/{productId}/adjust | public | 200 |
| POST /internal/api/inventory/reserve | public | 200 |
| POST /internal/api/inventory/release | public | 200 |
| POST /internal/api/inventory/confirm | public | 200 |

## 1. Create product

`POST http://localhost:8083/api/products`:

```json
{
  "sku": "MOBILE-001",
  "name": "Samsung Galaxy S25",
  "description": "5G Smartphone",
  "price": 74999.00,
  "initialQuantity": 100
}
```

201 Created, `Location: /api/products/1001`:

```json
{
  "id": 1001,
  "sku": "MOBILE-001",
  "name": "Samsung Galaxy S25",
  "description": "5G Smartphone",
  "price": 74999.00,
  "status": "ACTIVE",
  "createdAt": "2026-01-01T00:00:00",
  "updatedAt": "2026-01-01T00:00:00"
}
```

One product and one inventory row are inserted in the same transaction: total=100, reserved=0. SKU matching is canonical uppercase; a duplicate returns 409 `DUPLICATE_SKU`, including a concurrent insert race.

## 2. Get product

`GET http://localhost:8083/api/products/1001`

200 returns the same product structure above. Missing or deleted products return 404. No database changes.

## 3. Get all products

`GET http://localhost:8083/api/products?page=0&size=10&sort=price,desc`

```json
{
  "content": [{
    "id": 1001, "sku": "MOBILE-001", "name": "Samsung Galaxy S25",
    "description": "5G Smartphone", "price": 74999.00, "status": "ACTIVE",
    "createdAt": "2026-01-01T00:00:00", "updatedAt": "2026-01-01T00:00:00"
  }],
  "page": 0, "size": 10, "totalElements": 1, "totalPages": 1, "last": true
}
```

Size is 1–100. Sort accepts one field (`id`, `sku`, `name`, `price`, `createdAt`, `updatedAt`) and `asc` or `desc`. ID is a stable secondary sort. Unknown fields or invalid pagination return 400. No database changes.

## 4. Search product

`GET http://localhost:8083/api/products/search?keyword=SAMSUNG&page=0&size=10`

200 returns the page structure above. Searches name or SKU case-insensitively using SQL, excludes deleted rows, and treats `%` and `_` in the keyword literally. An empty result is an empty content array with totalElements=0 and totalPages=0. No database changes.

## 5. Update product

`PUT http://localhost:8083/api/products/1001`:

```json
{"name":"Samsung Galaxy S25 Ultra","description":"Updated Smartphone","price":84999.00}
```

200:

```json
{
  "id": 1001, "sku": "MOBILE-001", "name": "Samsung Galaxy S25 Ultra",
  "description": "Updated Smartphone", "price": 84999.00, "status": "ACTIVE",
  "createdAt": "2026-01-01T00:00:00", "updatedAt": "2026-01-01T01:00:00"
}
```

Updates product fields and version/timestamp; inventory is unchanged. This is a full details update: name and price are required; an omitted description becomes null. Attempts to send ID, SKU, stock, or status return 400.

## 6. Delete product

`DELETE http://localhost:8083/api/products/1001`.

204 No Content, empty body. Sets status=DELETED; no row is physically removed. Repeating delete on that existing deleted ID returns 204. Missing IDs return 404. Inventory and reservations remain so pending payments can complete. Run this after inventory exercises or use a different product.

## 7. Get inventory / check availability

`GET http://localhost:8083/internal/api/inventory/1001`:

```json
{"productId":1001,"totalQuantity":100,"reservedQuantity":20,"availableQuantity":80}
```

A client can compare requested quantity with availableQuantity, but this is a snapshot, not a stock guarantee. Only a successful reservation allocates units. Internal lookup also supports deleted products with outstanding inventory. No database changes.

## 8. Adjust inventory

`PATCH http://localhost:8083/internal/api/inventory/1001/adjust`:

```json
{"quantityChange":50,"reason":"New stock received"}
```

Starting at total=100/reserved=20, 200:

```json
{"productId":1001,"totalQuantity":150,"reservedQuantity":20,"availableQuantity":130}
```

Total and inventory version change; reservation rows do not. Negative adjustments remove stock but must leave total at least reserved. Zero or overflowing adjustments return 409 `INVALID_INVENTORY_OPERATION`. A missing/blank reason returns 400. Adjustment is an administrative delta operation and is **not idempotent**: do not blindly retry it after a network failure. Order reservation/release/confirmation are the retry-safe APIs.

## 9. Reserve inventory

`POST http://localhost:8083/internal/api/inventory/reserve`:

```json
{"orderId":5001,"items":[{"productId":1001,"quantity":2}]}
```

200:

```json
{"orderId":5001,"items":[{"productId":1001,"quantity":2,"status":"RESERVED"}]}
```

Starting at total=100/reserved=0: total remains 100, reserved becomes 2, available becomes 98. Adds one reservation row for (5001,1001).

For multiple products, first create a second product, then submit:

```json
{"orderId":5003,"items":[{"productId":1001,"quantity":2},{"productId":1002,"quantity":1}]}
```

200:

```json
{"orderId":5003,"items":[{"productId":1001,"quantity":2,"status":"RESERVED"},{"productId":1002,"quantity":1,"status":"RESERVED"}]}
```

Every line commits together. Failure on any product rolls back counters and reservations for all lines. Duplicate product IDs return 400.

## 10. Release inventory

`POST http://localhost:8083/internal/api/inventory/release`:

```json
{"orderId":5001}
```

200:

```json
{"orderId":5001,"items":[{"productId":1001,"quantity":2,"status":"RELEASED"}]}
```

From the reservation above: total=100, reserved=0, available=100. Reservation becomes RELEASED. Repeated release changes nothing. Release of a CONFIRMED or unknown order returns 409.

## 11. Confirm inventory

Create a new reservation for order 5002, then `POST http://localhost:8083/internal/api/inventory/confirm`:

```json
{"orderId":5002}
```

200:

```json
{"orderId":5002,"items":[{"productId":1001,"quantity":2,"status":"CONFIRMED"}]}
```

From total=100/reserved=2: total=98, reserved=0, available=98. Reservation becomes CONFIRMED. Repeated confirmation changes nothing. Confirming a RELEASED or unknown order returns 409.

## 12. Insufficient stock

With available=5, request:

```json
{"orderId":5010,"items":[{"productId":1001,"quantity":10}]}
```

409:

```json
{
  "timestamp":"2026-01-01T00:00:00Z",
  "status":409,
  "code":"INSUFFICIENT_STOCK",
  "message":"Insufficient stock for product 1001",
  "path":"/internal/api/inventory/reserve",
  "availableQuantity":5,
  "requestedQuantity":10
}
```

No counters or reservation rows change.

## 13. Duplicate reservation

Send the exact same orderId/items again. 200 returns the existing current reservation response; it never increases reserved a second time. After release it returns RELEASED; after confirmation it returns CONFIRMED. Item order may differ. Changing the quantity or product set for that order returns 409 `INVALID_INVENTORY_OPERATION` with no database change.

## 14. Concurrent stock reservation

Create a new ACTIVE product with initialQuantity=1. Set its ID in PowerShell 7:

```powershell
./scripts/concurrent-reservation.ps1 -ProductId 1003
```

The script sends two distinct order IDs in parallel. Expected: one 200 RESERVED and one 409 (INSUFFICIENT_STOCK or CONCURRENT_INVENTORY_CONFLICT). Final inventory is:

```json
{"productId":1003,"totalQuantity":1,"reservedQuantity":1,"availableQuantity":0}
```

Exactly one reservation row exists. Real HTTP timing may allow the loser to read the already-updated stock; the PostgreSQL integration test uses a barrier to deterministically force the optimistic-lock path. Postman's normal collection runner is sequential, so use the supplied script or two simultaneous clients for this scenario.

## Validation checks

All endpoints are public in the current application. Invalid JSON, missing required fields, invalid path/query values, and business conflicts return the documented 400 or 409 responses.

All errors use timestamp/status/code/message/path, with fieldErrors for body validation and quantity details for insufficient stock. Internal exception messages and SQL are not returned.

