import { Pipe, PipeTransform } from "@angular/core";
import { environment } from "../../../environment/environment";

/**
 * Resolves a stored image path into something an <img src> can load.
 *
 * The backend stores a path like "/uploads/menu-images/abc.jpg". That works
 * as-is in development (dev-server proxy) and in same-origin production. When
 * the API lives on a different origin, environment.uploadBaseUrl is prefixed in.
 *
 * Absolute URLs (someone typed a full https://... image URL) pass through
 * untouched. Empty values become null so the template can show a placeholder.
 *
 * Usage: <img [src]="item.imageUrl | imageUrl">
 */
@Pipe({
    name: 'imageUrl',
    standalone: true,
    pure: true
})
export class ImageUrlPipe implements PipeTransform {

    transform(value: string | null | undefined): string | null {
        if (!value) {
            return null;
        }

        const trimmed = value.trim();
        if (!trimmed) {
            return null;
        }

        // Already absolute (http://, https://, //cdn..., data:, blob:) -> leave as-is.
        if (/^(https?:)?\/\//i.test(trimmed) || /^(data|blob):/i.test(trimmed)) {
            return trimmed;
        }

        const base = (environment.uploadBaseUrl ?? '').replace(/\/+$/, '');
        const path = trimmed.startsWith('/') ? trimmed : '/' + trimmed;

        return base + path;
    }
}
