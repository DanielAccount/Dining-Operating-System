/** Mirrors the backend OrderResponse. Amounts are server-computed. */
export interface Order {
    id: number;
    status: OrderStatus;
    orderType: string;
    source: OrderSource;
    tableNumber: string | null;
    sessionCode: string | null;
    customerNote: string | null;
    customerPhoneNumber: string | null;
    invoiceNumber: string | null;
    subtotal: number;
    taxAmount: number;
    discountAmount: number;
    serviceCharge: number;
    finalAmount: number;
    createdAt: string;
    items: OrderLine[];
}

export interface OrderLine {
    id: number;
    itemNameSnapshot: Record<string, string>;
    quantity: number;
    unitPrice: number;
    lineTotal: number;
    itemStatus: string;
    modifiers: { id: number; name: string; price: number }[];
}

/** One entry in the status story - the staff detail view. */
export interface OrderStatusHistory {
    id: number;
    status: OrderStatus;
    remarks: string | null;
    createdAt: string;
    createdBy: number | null;
}

export interface OrderDetail {
    order: Order;
    history: OrderStatusHistory[];
}

export type OrderStatus =
    | 'PENDING' | 'ACCEPTED' | 'PREPARING' | 'READY' | 'COMPLETED' | 'CANCELLED';

export type OrderSource = 'QR_CUSTOMER' | 'WAITER' | 'CASHIER';

/** A table on the waiter's picker - the create-order form picks from these. */
export interface OrderTable {
    id: number;
    tableNumber: string;
}

/** Cart line for a staff-created order: menu item + quantity + options. */
export interface StaffCartLine {
    key: string;
    itemId: number;
    name: string;
    price: number;
    quantity: number;
    modifierOptions: { id: number; name: string; price: number }[];
}
