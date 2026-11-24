import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { UserRole } from '../../core/auth/auth.models';

interface NavItem {
  label: string;
  path: string;
  icon: string;
  roles: UserRole[];
}

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.css']
})
export class MainLayoutComponent {
  authService = inject(AuthService);
  private router = inject(Router);

  sidebarOpen = false;

  navItems: NavItem[] = [
    // CUSTOMER NAV
    { label: 'Tổng quan', path: '/customer/dashboard', icon: 'dashboard', roles: ['CUSTOMER'] },
    { label: 'Tài khoản của tôi', path: '/customer/accounts', icon: 'account_balance', roles: ['CUSTOMER'] },
    { label: 'Mở tài khoản', path: '/customer/accounts/open', icon: 'add_card', roles: ['CUSTOMER'] },
    { label: 'Chuyển tiền', path: '/customer/transfers', icon: 'send', roles: ['CUSTOMER'] },
    { label: 'Lịch sử giao dịch', path: '/customer/transactions', icon: 'receipt_long', roles: ['CUSTOMER'] },

    // STAFF NAV
    { label: 'Tổng quan chi nhánh', path: '/staff/dashboard', icon: 'store', roles: ['STAFF'] },

    // AUDITOR NAV
    { label: 'Nhật ký kiểm toán', path: '/auditor/dashboard', icon: 'fact_check', roles: ['AUDITOR'] },

    // ADMIN NAV
    { label: 'Quản trị hệ thống', path: '/admin/dashboard', icon: 'admin_panel_settings', roles: ['ADMIN'] },

    // COMMON
    { label: 'Hồ sơ cá nhân', path: '/profile', icon: 'person', roles: ['CUSTOMER', 'STAFF', 'AUDITOR', 'ADMIN'] }
  ];

  get filteredNavItems(): NavItem[] {
    const roles = this.authService.currentAuthState.roles;
    return this.navItems.filter((item) => item.roles.some((r) => roles.includes(r)));
  }

  toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
  }

  closeSidebar(): void {
    this.sidebarOpen = false;
  }

  onLogout(): void {
    this.authService.logout();
  }
}

