# Inventory consistency

## Counters and states

Available stock = total quantity − reserved quantity. Total and reserved are nonnegative, and reserved cannot exceed total. Entity methods and PostgreSQL CHECK constraints enforce these bounds.

| Operation, quantity 2 | Total | Reserved | Available | Reservation |
|---|---:|---:|---:|---|
| Initial | 100 | 0 | 100 | none |
| Reserve | 100 | 2 | 98 | RESERVED |
| Confirm after reserve | 98 | 0 | 98 | CONFIRMED |
| Release instead of confirm | 100 | 0 | 100 | RELEASED |

Confirmation removes the sold units from both total and reserved, leaving available unchanged. Release removes only the reservation. An adjustment changes total by a signed amount; it cannot reduce total below reserved or overflow an integer.

## Optimistic locking

Every inventory row has a JPA `@Version Long version`. Hibernate includes the old version in an update:

```sql
UPDATE inventory
SET reserved_quantity = ?, total_quantity = ?, version = ?
WHERE id = ? AND version = ?;
```

If two transactions read version 0 and both try to reserve the last unit, only the first committed update can match version 0. The other updates zero rows and raises an optimistic locking exception. Its transaction rolls back, including every other inventory update and every reservation insert in that request. The API returns 409 `CONCURRENT_INVENTORY_CONFLICT`. If the second customer reads after the winner commits, it instead sees insufficient availability and receives 409 `INSUFFICIENT_STOCK`.

This avoids overselling without serializing all customers at the application layer. Multi-item operations process product IDs in sorted order and Hibernate orders updates, reducing database deadlock opportunities. There is no silent retry loop inside an already failed transaction. Clients may retry the entire request in a fresh transaction; a stock failure is a business outcome, not a reason to retry indefinitely.

Reservation records also have versions. Product records have versions and reservation reads use an optimistic product lock, so a concurrent deletion/update can invalidate the read rather than silently reserving a product with stale status.

## Idempotency across instances

The unique key `(order_id, product_id)` alone is insufficient: two requests for the same order with disjoint item lists could both insert. Before reading reservations, reserve/release/confirm acquire a PostgreSQL transaction advisory lock keyed by order ID. It serializes the whole order operation, even when no reservation row exists yet. It works across application instances using the same database and releases automatically on commit or rollback.

Different orders still compete through optimistic inventory versions. The advisory lock is scoped to this service/database's order-ID namespace; other applications must not share that lock namespace.

| Request | Existing state | Result |
|---|---|---|
| Reserve exact item/quantity set | any | 200, return existing current state; no stock change |
| Reserve changed set or quantity | any | 409, no stock change |
| Release | RESERVED | decrement reserved, mark RELEASED |
| Release | RELEASED | 200, no change |
| Confirm | RESERVED | decrement total and reserved, mark CONFIRMED |
| Confirm | CONFIRMED | 200, no change |
| Release | CONFIRMED | 409 |
| Confirm | RELEASED | 409 |
| Release/confirm | unknown order | 409 |
| New reserve | INACTIVE/DELETED product | 409 |
| Complete existing reservation | deleted product | allowed |

Duplicate product IDs in one request return 400. The request accepts up to 100 lines. Reordering the same items is an exact retry. A released order ID cannot be reused to make a new reservation.

Unknown-order completion is deliberately rejected instead of claiming success: no cancellation tombstone is created. The caller must sequence reserve before completion. A release sent before any reservation exists does not prevent a future reserve for that order. After network ambiguity, replay the same reserve request to recover its state, then complete it. No distributed transaction or automatic expiry is implemented; Order Service will own eventual Saga compensation and abandoned-reservation handling.

## Tests

`PostgresInventoryIT` uses two real connections and a barrier to force both orders to observe the same inventory version. It asserts one success, one optimistic conflict, a single reservation row, and counters within bounds. Additional tests cover simultaneous identical retries, changed payloads for one order, terminal-state races, double confirmation, atomic multi-item rollback, and completing reservations after soft deletion.

Mockito tests exercise decision logic, but cannot prove database rollback or locking. That is why the PostgreSQL suite is separate and must run before deployment.

