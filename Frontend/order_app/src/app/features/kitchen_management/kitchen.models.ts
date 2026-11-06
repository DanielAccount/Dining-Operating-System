/** Mirrors the backend Phase 5 KitchenItemResponse. */
export interface KitchenItem {
  id: number;
  orderId: number;
  invoiceNumber: string | null;
  tableNumber: string | null;
  orderType: string;
  source: string;
  customerNote: string | null;
  orderCreatedAt: string;
  itemNameSnapshot: Record<string, string>;
  quantity: number;
  itemStatus: KitchenItemStatus;
  orderStatus: string;
  modifiers: KitchenModifier[];
  itemCreatedAt: string;
  readyAt: string | null;
  servedAt: string | null;
}

export interface KitchenModifier {
  id: number;
  name: string;
}

export type KitchenItemStatus =
  | 'PENDING'
  | 'PREPARING'
  | 'READY'
  | 'SERVED'
  | 'CANCELLED';

/** Client-side grouping: one ticket can contain independently moving lines. */
export interface KitchenTicket {
  orderId: number;
  invoiceNumber: string | null;
  tableNumber: string | null;
  orderType: string;
  source: string;
  customerNote: string | null;
  orderCreatedAt: string;
  orderStatus: string;
  items: KitchenItem[];
}
