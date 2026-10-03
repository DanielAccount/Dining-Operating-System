import { Routes } from '@angular/router';
import { SideBarComponent } from './shared/components/sidebar/sidebar.component';
import { permissionGuard } from './core/guards/permission-guard';
import { AuthGuard } from './core/auth/auth.guard';



export const routes: Routes = [
    {
        path: 'login',
        loadComponent: () => import('./features/login/LoginComponent')
            .then(c => c.LoginComponent)
    },

    {
        path: 'management',
        component: SideBarComponent,
        canActivate: [AuthGuard],
        children: [
            {
                path: 'dashboard',
                loadComponent: () => import('./features/finance_dashboard/finance.dashboard')
                    .then(c => c.FinanceDashboard),
                    canActivate: [permissionGuard('DASHBOARD_VIEW')]
            },

            {
                path: 'menu_management',
                loadComponent: () => import('./features/menu_management/menu_management')
                    .then(c => c.MenuManagement),
                    canActivate: [permissionGuard('MENU_MANAGE')],
                children: [

                    {path:'', redirectTo: 'products_management', pathMatch: 'full'},

                    {
                        path: 'products_management',
                        loadComponent: () => import('./features/menu_management/products/products.component')
                             .then(c => c.ProductsComponent)
                    },

                    {
                        path: 'categories_management',
                        loadComponent: () => import('./features/menu_management/categories/categories.component')
                             .then(c => c.CategoriesComponent)
                    },

                    {
                        path: 'modifiers_management',
                        loadComponent: () => import('./features/menu_management/modifiers/modifiers.component')
                             .then(c => c.ModifiersComponent)
                    },

                    {
                        path: 'branch_pricing_management',
                        loadComponent: () => import('./features/menu_management/branch-pricing/branch-pricing')
                             .then(c => c.BranchPricingComponent)
                    },

                    

                ]
                
            },

            {
                path: 'user_manage',
                loadComponent: () => import('./features/user_management/user_management')
                    .then(c => c.UserManagementComponent),
                canActivate: [permissionGuard('USER_MANAGE')]
            },

            {
                path: 'kitchen_display',
                loadComponent: () => import('./features/kitchen_management/kitchen_management')
                    .then(c => c.KitchenManagement),
                     canActivate: [permissionGuard('KDS_VIEW')]
            },

            
            {
                path: 'store',
                loadComponent: () => import('./features/store/store.component')
                    .then(c => c.StoreComponent),
                canActivate: [permissionGuard('MENU_VIEW')]
            },

            {
               path: 'payments',
               loadComponent: () => import('./features/payments/payments')
                   .then(c => c.PaymentsComponent),
               canActivate: [permissionGuard('PAYMENT_VIEW')]
            }

          
        ]
   
    },

    {
        path: 'kitchen',
        component: SideBarComponent,
        canActivate: [AuthGuard],
        children: [
            {
                path: 'dashboard',
                loadComponent: () => import('./features/kitchen_management/kitchen_management')
                    .then(c => c.KitchenManagement),
                canActivate: [permissionGuard('KDS_VIEW')]
            },
          
        ]
     
    },


   

    {
        path:'',
        redirectTo: 'login',
        pathMatch: 'full'
    }
];
