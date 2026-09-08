import { Component } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { RouterLink, RouterLinkActive, RouterOutlet } from "@angular/router";

@Component({
    selector: 'menu_management',
    standalone: true,
    imports: [ReactiveFormsModule, RouterLink, RouterLinkActive, RouterOutlet], 
    templateUrl: './menu_management.html'
})
export class MenuManagement{

}