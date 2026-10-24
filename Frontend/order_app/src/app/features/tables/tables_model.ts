/**
 * Mirrors the backend's TableResponse. qrUrl arrives fully assembled by the
 * API ({app.public.base-url}/t/{qrSlug}) so this client never builds QR
 * links itself - the deployment's base URL is the backend's business, not
 * the frontend's.
 */
export interface TableResponse {
    id: number;
    tableNumber: string;
    qrSlug: string;
    qrUrl: string;
    createdAt: string;
    updatedAt: string;
}
