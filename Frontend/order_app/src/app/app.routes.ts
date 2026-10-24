import { Routes } from '@angular/router';
import { SideBarComponent } from './shared/components/sidebar/sidebar.component';
import { permissionGuard } from './core/guards/permission-guard';
import { AuthGuard } from './core/auth/auth.guard';

// ─────────────────────────────────────────────────────────────────────
// Route map
//
// First URL segment = audience:
//   /app/…  staff (login + permission guarded, one sidebar shell)
//   /t/:slug customer (PUBLIC — printed table QR codes point here)
//
// Naming rules (apply to every future route):
//   • kebab-case, lowercase
//   • plural nouns for collections (/users, /orders, /tables)
//   • resource names only — no "_management"/"_manage"/"_display" suffixes
//   • one canonical URL per screen
//   • '' redirects to the most-used child
//
// Sidebar labels don't have to match URLs ("Menu Management" over /app/menu
// is fine). Phases 3–8 already have reserved slots:
//   Phase 3: /app/tables            (table mgmt + QR download)
//   Phase 4: /app/orders            (already stubbed — waiter landing)
//   Phase 5: /app/kitchen           (already stubbed — KDS)
//   Phase 6: /app/service-requests
//   Phase 7: /app/payments          (already stubbed)
//   Phase 8: /app/reports
// ─────────────────────────────────────────────────────────────────────
export const routes: Routes = [
    {
        path: 'login',
        loadComponent: () => import('./features/login/LoginComponent')
            .then(c => c.LoginComponent)
    },

    // ── Staff area (authenticated) ──────────────────────────────────
    {
        path: 'app',
        component: SideBarComponent,
        canActivate: [AuthGuard],
        children: [
            // Permission-based landing: owner/manager → dashboard,
            // chef → kitchen, waiter → orders, cashier → payments.
            // See AuthService.landingUrl().
            {
                path: '',
                pathMatch: 'full',
                loadComponent: () => import('./features/landing/landing')
                    .then(c => c.LandingComponent)
            },

            {
                path: 'dashboard',
                loadComponent: () => import('./features/finance_dashboard/finance.dashboard')
                    .then(c => c.FinanceDashboard),
                canActivate: [permissionGuard('DASHBOARD_VIEW')]
            },

            {
                path: 'orders',
                loadComponent: () => import('./features/orders/orders')
                    .then(c => c.OrdersComponent),
                canActivate: [permissionGuard('ORDER_VIEW')]
            },

            {
                path: 'kitchen',
                loadComponent: () => import('./features/kitchen_management/kitchen_management')
                    .then(c => c.KitchenManagement),
                canActivate: [permissionGuard('KDS_VIEW')]
            },

            {
                path: 'payments',
                loadComponent: () => import('./features/payments/payments')
                    .then(c => c.PaymentsComponent),
                canActivate: [permissionGuard('PAYMENT_VIEW')]
            },

            {
                path: 'menu',
                loadComponent: () => import('./features/menu_management/menu_management')
                    .then(c => c.MenuManagement),
                canActivate: [permissionGuard('MENU_MANAGE')],
                children: [
                    { path: '', redirectTo: 'products', pathMatch: 'full' },

                    {
                        path: 'products',
                        loadComponent: () => import('./features/menu_management/products/products.component')
                            .then(c => c.ProductsComponent)
                    },

                    {
                        path: 'categories',
                        loadComponent: () => import('./features/menu_management/categories/categories.component')
                            .then(c => c.CategoriesComponent)
                    },

                    {
                        path: 'modifiers',
                        loadComponent: () => import('./features/menu_management/modifiers/modifiers.component')
                            .then(c => c.ModifiersComponent)
                    },

                    {
                        path: 'branch-pricing',
                        loadComponent: () => import('./features/menu_management/branch-pricing/branch-pricing')
                            .then(c => c.BranchPricingComponent)
                    },
                ]
            },

            {
                path: 'users',
                loadComponent: () => import('./features/user_management/user_management')
                    .then(c => c.UserManagementComponent),
                canActivate: [permissionGuard('USER_MANAGE')]
            },

            {
                path: 'tables',
                loadComponent: () => import('./features/tables/tables.component')
                    .then(c => c.TablesComponent),
                canActivate: [permissionGuard('TABLE_VIEW')]
            },

            // Staff-side preview of the customer storefront.
            {
                path: 'store-preview',
                loadComponent: () => import('./features/store/store.component')
                    .then(c => c.StoreComponent),
                canActivate: [permissionGuard('MENU_VIEW')]
            },

            // Authenticated but without any usable permissions.
            // Intentionally unguarded — otherwise zero-permission
            // users could never land anywhere.
            {
                path: 'no-access',
                loadComponent: () => import('./shared/components/no-access/no-access.component')
                    .then(c => c.NoAccessComponent)
            },
        ]
    },

    // ── Customer area (PUBLIC) ───────────────────────────────────────
    // Short path on purpose: it is baked into printed QR codes.
    // Must never change once Phase 3 stickers are on tables.
    {
        path: 't/:slug',
        loadComponent: () => import('./features/storefront/storefront')
            .then(c => c.StorefrontComponent)
    },

  
    // Root: let the landing logic decide (login if not authenticated).
    { path: '', pathMatch: 'full', redirectTo: 'app' },

    // 404 — must stay last.
    {
        path: '**',
        loadComponent: () => import('./shared/components/not-found/not-found.component')
            .then(c => c.NotFoundComponent)
    }
];
