import { HttpErrorResponse } from '@angular/common/http';

/**
 * One place that turns a failed HTTP call into something worth showing a user.
 *
 * The backend's GlobalExceptionHandler always answers with a JSON body carrying
 * a human-readable `message` - including field-keyed validation failures
 * ("basePrice: Base price cannot be negative") and 409 conflicts ("Cannot delete
 * category 12 because it still contains menu items"). Discarding that and
 * showing "Something went wrong" is how a user ends up retrying a request that
 * was never going to succeed, so every component reads the message through here.
 */
export function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    // The server always sends a message; a proxy or a crashed connection does not.
    const body = error.error as { message?: string } | string | null;

    if (body && typeof body === 'object' && body.message) {
      return body.message;
    }
    if (typeof body === 'string' && body.trim() && !body.trim().startsWith('<')) {
      return body;
    }

    switch (error.status) {
      case 0:
        return 'Cannot reach the server. Check your connection and try again.';
      case 401:
        return 'Your session has expired. Please sign in again.';
      case 403:
        return 'You do not have permission to do that.';
      case 409:
        return 'That change conflicts with existing data. Refresh and try again.';
      case 413:
        return 'That file is too large.';
    }
  }

  return fallback;
}
