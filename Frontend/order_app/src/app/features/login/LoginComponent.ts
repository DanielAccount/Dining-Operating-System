import { Component, OnInit } from "@angular/core";
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from "@angular/forms";

import { Router } from "@angular/router";
import { AuthService } from "../../core/auth/auth.service";

@Component({
    selector: 'app-login',
    standalone: true,
    imports: [ReactiveFormsModule],
    templateUrl: './login.html',
})
export class LoginComponent implements OnInit {
    loginForm!: FormGroup;

    isLoading = false;
    errorMessage = '';
    isPasswordVisible = false;

    constructor(private fb: FormBuilder, private authService: AuthService, private router: Router) {}

    ngOnInit(): void {
        // Reverse guard: already-logged-in users go straight to their landing page.
        if (this.authService.isAuthenticated()) {
            this.router.navigateByUrl(this.authService.landingUrl());
            return;
        }

        this.loginForm = this.fb.group({
            email: ['', [Validators.required, Validators.email]],
            password: ['', Validators.required]
        });
    }

    togglePasswordVisibility(): void {
        this.isPasswordVisible = !this.isPasswordVisible;
    }

    onSubmit(): void {
        if (this.loginForm.invalid) return;

        this.isLoading = true;

        const { email, password } = this.loginForm.value;

        this.authService.login(email!, password!)
            .subscribe({
                next: (response) => {
                    console.log(response);
                    this.isLoading = false;
                    this.router.navigateByUrl(this.authService.landingUrl());
                },
                error: (err) => {
                    console.error(err);
                    this.isLoading = false;
                    this.errorMessage = 'Invalid email or password.';
                }
            });
    }
}
