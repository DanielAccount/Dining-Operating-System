import { Component, inject } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { AuthService } from "../../core/auth/auth.service";

@Component({
    selector: 'menu_management',
    standalone: true,
    imports: [ReactiveFormsModule], 
    templateUrl: './store.html'
})
export class StoreComponent{
    protected authService = inject(AuthService);
}