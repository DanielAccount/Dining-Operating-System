import { Component } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";

@Component({
    selector: 'finance-dashboard',
    standalone: true,
    imports: [ReactiveFormsModule], 
    templateUrl: './finance.dashboard.html'
   
})
export class FinanceDashboard {

}