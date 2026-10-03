import { Component, inject } from "@angular/core";
import { RouterLink, RouterLinkActive, RouterOutlet } from "@angular/router";
import { HasPermissionDirective } from "../../directives/has_permission_directuve";
import { AuthService } from "../../../core/auth/auth.service";
import { BranchContextService } from "../../../core/branch/branch-context.service";

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, HasPermissionDirective],
  templateUrl: './sidebar.component.html'
})
export class SideBarComponent {
  protected authService = inject(AuthService)
  protected branchContext = inject(BranchContextService)

  selectedLanguage: 'en' | 'my' = 'en';

  /** Owner picks a branch; branch-scoped staff get their own, read-only. */
  protected onBranchChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.branchContext.select(value === '' ? null : Number(value));
  }

onLanguageToggle(event: Event): void {
  const isChecked = (event.target as HTMLInputElement).checked;
  this.selectedLanguage = isChecked ? 'my' : 'en';
  // Add your logic to trigger language change (e.g., TranslateService)
}

    onLogout(): void {
        this.authService.logout();
    }
}
