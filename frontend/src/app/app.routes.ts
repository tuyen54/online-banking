import { Routes } from '@angular/router';
import { MainLayoutComponent } from './shared/layouts/main-layout.component';
import { HomeComponent } from './features/home/home.component';
import { UnauthorizedComponent } from './features/common/unauthorized.component';
import { NotFoundComponent } from './features/common/not-found.component';
import { ProfileComponent } from './features/profile/profile.component';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: '',
    component: HomeComponent
  },
  {
    path: 'unauthorized',
    component: UnauthorizedComponent
  },
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      // CUSTOMER ROUTES
      {
        path: 'customer/dashboard',
        loadComponent: () => import('./features/customer/dashboard/dashboard.component').then(m => m.CustomerDashboardComponent),
        canActivate: [roleGuard],
        data: { roles: ['CUSTOMER'] }
      },
      {
        path: 'customer/accounts',
        loadComponent: () => import('./features/customer/accounts/accounts.component').then(m => m.CustomerAccountsComponent),
        canActivate: [roleGuard],
        data: { roles: ['CUSTOMER'] }
      },
      {
        path: 'customer/accounts/open',
        loadComponent: () => import('./features/customer/open-account/open-account.component').then(m => m.OpenAccountComponent),
        canActivate: [roleGuard],
        data: { roles: ['CUSTOMER'] }
      },
      {
        path: 'customer/transfers',
        loadComponent: () => import('./features/customer/transfer/transfer.component').then(m => m.CustomerTransferComponent),
        canActivate: [roleGuard],
        data: { roles: ['CUSTOMER'] }
      },
      {
        path: 'customer/transactions',
        loadComponent: () => import('./features/customer/transactions/transactions.component').then(m => m.CustomerTransactionsComponent),
        canActivate: [roleGuard],
        data: { roles: ['CUSTOMER'] }
      },

      // STAFF ROUTES
      {
        path: 'staff/dashboard',
        loadComponent: () => import('./features/staff/staff-dashboard.component').then(m => m.StaffDashboardComponent),
        canActivate: [roleGuard],
        data: { roles: ['STAFF'] }
      },

      // AUDITOR ROUTES
      {
        path: 'auditor/dashboard',
        loadComponent: () => import('./features/auditor/auditor-dashboard.component').then(m => m.AuditorDashboardComponent),
        canActivate: [roleGuard],
        data: { roles: ['AUDITOR'] }
      },

      // ADMIN ROUTES
      {
        path: 'admin/dashboard',
        loadComponent: () => import('./features/admin/admin-dashboard.component').then(m => m.AdminDashboardComponent),
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] }
      },

      // COMMON PROFILE
      {
        path: 'profile',
        component: ProfileComponent,
        canActivate: [roleGuard],
        data: { roles: ['CUSTOMER', 'STAFF', 'AUDITOR', 'ADMIN'] }
      }
    ]
  },
  {
    path: '**',
    component: NotFoundComponent
  }
];
