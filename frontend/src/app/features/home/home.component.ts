import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="home-container">
      <div class="hero-card bank-card">
        <div class="hero-icon">🏛️</div>
        <h1>Hệ Thống Ngân Hàng Trực Tuyến</h1>
        <p class="hero-subtitle">
          Nền tảng ngân hàng số hiện đại, an toàn bảo mật cao với xác thực OIDC Keycloak, kiểm soát truy cập RBAC/ABAC và sổ cái kép tài chính.
        </p>

        <div class="auth-box mt-4">
          <div *ngIf="!(authService.authState$ | async)?.isAuthenticated">
            <button class="btn btn-primary btn-lg" (click)="login()">
              Đăng nhập qua Keycloak
            </button>
            <p class="form-text mt-2">Hệ thống chuyển hướng an toàn đến cổng đăng nhập xác thực tập trung Keycloak SSO.</p>
          </div>

          <div *ngIf="(authService.authState$ | async)?.isAuthenticated as isAuth">
            <div class="alert alert-success">
              Đang đăng nhập dưới danh tính: <strong>{{ (authService.authState$ | async)?.displayName }}</strong> ({{ (authService.authState$ | async)?.primaryRole }})
            </div>
            <button class="btn btn-primary" (click)="navigateToDashboard()">
              Đi đến Bảng điều khiển của bạn
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .home-container { max-width: 900px; margin: 40px auto; text-align: center; }
    .hero-card { padding: 48px 32px; }
    .hero-icon { font-size: 4rem; margin-bottom: 16px; }
    .hero-subtitle { font-size: 1.1rem; color: #4b5563; max-width: 650px; margin: 0 auto; }
    .btn-lg { padding: 12px 28px; font-size: 1.1rem; }
    .mt-4 { margin-top: 28px; }
    .mt-2 { margin-top: 8px; }
  `]
})
export class HomeComponent implements OnInit {
  authService = inject(AuthService);
  private router = inject(Router);

  ngOnInit(): void {
    if (this.authService.currentAuthState.isAuthenticated) {
      this.navigateToDashboard();
    }
  }

  login(): void {
    this.authService.login();
  }

  navigateToDashboard(): void {
    const role = this.authService.currentAuthState.primaryRole;
    if (role === 'CUSTOMER') {
      this.router.navigate(['/customer/dashboard']);
    } else if (role === 'STAFF') {
      this.router.navigate(['/staff/dashboard']);
    } else if (role === 'AUDITOR') {
      this.router.navigate(['/auditor/dashboard']);
    } else if (role === 'ADMIN') {
      this.router.navigate(['/admin/dashboard']);
    } else {
      this.router.navigate(['/profile']);
    }
  }
}

