import { HttpClient } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { environment } from "../../../../environment/environment";
import { Observable } from "rxjs";
import { Category, CategoryCreateRequest } from "../models/categories_model";

@Injectable({ providedIn: 'root' })
export class CategoryService {

    private readonly http = inject(HttpClient);
    private readonly baseUrl = `${environment.apiUrl}/categories`;


    getAll(): Observable<Category[]> {
        return this.http.get<Category[]>(this.baseUrl);
    }

    create(request: CategoryCreateRequest): Observable<Category> {
        return this.http.post<Category>(this.baseUrl, request);
    }

    update(id: number, request: CategoryCreateRequest): Observable<Category> {
        return this.http.put<Category>(`${this.baseUrl}/${id}`, request);
    }

    delete(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${id}`);
    }

}