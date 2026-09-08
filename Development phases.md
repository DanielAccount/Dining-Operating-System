Recommended MVP scope

Do not build everything.
For first paying customer, build only,
Must have:

Login
Tenant/branch isolation
Role permissions
Branch management
Table management
QR generation
Menu management
Modifier groups/options
Branch menu availability
Customer QR menu
Customer order submission
Kitchen display
Waiter screen
Service requests:
call waiter
request bill
pay cash
digital payment submitted
Payment manual verification
Cash collection
Basic owner dashboard
Basic reports:
today sales
orders
payment method breakdown
Subscription status enforcement
Offline banner + cached read data


Nice to have, later

Refunds
Promotions/discounts
Inventory
Delivery
Hotel features
Advanced analytics
Full offline sync
Payment gateway integration
Printer integration
Customer accounts
Loyalty



Phase 1: Foundation

Build:
Flyway migrations
Base entities
Tenant
Branch
User
Role
Permission
Auth login
JWT
Tenant resolver
Branch context
/api/me

Result:
User can login and see their tenant/branch/permissions.

Phase 2: Menu

Build:
Category
MenuItem
ModifierGroup
ModifierOption
MenuItemModifierGroup
BranchMenuItem
Menu management UI

Result:
Owner can create menu and assign to branch.

Phase 3: Tables and QR

Build:

TableEntity
QR slug generation
Table list UI
QR download/print
Customer QR menu endpoint

Result:
Customer scans QR and sees menu.

Phase 4: Ordering

Build:
TableSession
Order
OrderItem
OrderItemModifier
OrderStatusHistory
Customer order submission
Waiter order creation
Idempotency handling

Result:
Customer or waiter can submit order.

Phase 5: Kitchen

Build:
KDS query
Order item status updates
Mark preparing
Mark ready
Mark served
KDS polling/auto-refresh

Result:
Chef can see and process orders.

Phase 6: Service requests

Build:
Call waiter
Request bill
Pay cash
Digital payment submitted
Waiter/cashier ack/resolve

Result:
Customer can request help and staff can respond.


Phase 7: Payments

Build:
Payment creation
Cash pending
Cash collected
KBZPay/WavePay awaiting verification
Screenshot upload
Transaction reference
Cashier verify/reject
Payment reports

Result:
Order can be paid and verified.

Phase 8: Dashboards/reports

Build:
Owner dashboard
Manager dashboard
Cashier dashboard
Basic sales reports

Result:
Owner can see business performance