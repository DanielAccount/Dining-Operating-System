import { Directive, Input, inject, TemplateRef, ViewContainerRef } from "@angular/core";
import { AuthService } from "../../core/auth/auth.service";

@Directive({
    selector: '[appHasPermission]',
    standalone:true
})
export class HasPermissionDirective {
    
    private authService = inject(AuthService);
    private templateRef = inject(TemplateRef<any>);
    private viewContaimer = inject(ViewContainerRef);

    @Input() set appHasPermission(permission: string) {
        if (this.authService.hasPermission(permission)) {
            this.viewContaimer.createEmbeddedView(this.templateRef);
        }else {
            this.viewContaimer.clear();
        }
    }

    private static ngTemplateContextGuard(dir: HasPermissionDirective, ctx: unknown): ctx is any {
        return true;
    }

}