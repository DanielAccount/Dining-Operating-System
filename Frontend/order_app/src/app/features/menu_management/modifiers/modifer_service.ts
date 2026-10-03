import { inject, Injectable } from "@angular/core";
import { environment } from "../../../../environment/environment";
import { Observable } from "rxjs";
import { ModifierGroup, ModifierGroupCreateRequest } from "../models/modifiers_models";
import { HttpClient } from "@angular/common/http";

@Injectable({ providedIn: 'root' })
export class ModifierService{
    private readonly http = inject(HttpClient);
    private readonly baseUrl = `${environment.apiUrl}/modifier-groups`;

    getAll(): Observable<ModifierGroup[]> {
        return this.http.get<ModifierGroup[]>(this.baseUrl);
    }

    create(request : ModifierGroupCreateRequest): Observable<ModifierGroup> {
        return this.http.post<ModifierGroup>(this.baseUrl, request);
    }

    update(id: number, request: ModifierGroupCreateRequest): Observable<ModifierGroup> {
        return this.http.put<ModifierGroup>(`${this.baseUrl}/${id}`, request);
    }

    delete(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${id}`);
    }

}