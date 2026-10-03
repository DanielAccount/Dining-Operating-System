import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../../environment/environment";
import { Observable } from "rxjs";
import { MenuItem, MenuItemCreateRequest, MenuItemUpdateRequest } from "../models/products_model";

@Injectable({ providedIn: 'root' })
export class ProductService {

    private readonly http = inject(HttpClient);
    private readonly baseUrl = `${environment.apiUrl}/menu-items`;

    getAll(): Observable<MenuItem[]> {
        return this.http.get<MenuItem[]>(this.baseUrl);
    }

    getById(id: number): Observable<MenuItem> {
        return this.http.get<MenuItem>(`${this.baseUrl}/${id}`);
    }

    getByCategory(categoryId: number): Observable<MenuItem[]> {
        return this.http.get<MenuItem[]>(`${this.baseUrl}/category/${categoryId}`);
    }

    create(request: MenuItemCreateRequest): Observable<MenuItem> {
        return this.http.post<MenuItem>(this.baseUrl, request);
    }

    update(id: number, request: MenuItemUpdateRequest): Observable<MenuItem> {
        return this.http.put<MenuItem>(`${this.baseUrl}/${id}`, request);
    }

    delete(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${id}`);
    }

    /**
     * Uploads a single image file and returns the stored public path,
     * e.g. "/uploads/menu-items/6f1c....jpg".
     *
     * Do not set Content-Type manually: passing FormData lets the browser
     * generate the multipart boundary that Spring requires.
     */
    uploadImageFile(file: File): Observable<{ imageUrl: string }> {
        const formData = new FormData();
        formData.append('file', file);

        return this.http.post<{ imageUrl: string }>(
            `${this.baseUrl}/images/upload`,
            formData
        );
    }
}
