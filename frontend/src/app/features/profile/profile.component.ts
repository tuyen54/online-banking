import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="profile-container">
      <div class="page-header">
        <h2>Hồ sơ tài khoản & Danh tính</h2>
        <p class="text-muted">Thông tin xác thực danh tính từ Keycloak và cơ sở dữ liệu hệ thống ngân hàng.</p>
      </div>

      <div class="bank-card" *ngIf="authService.authState$ | async as auth">
        <div class="profile-header">
          <div class="avatar-circle">
            {{ (auth.displayName || auth.username || 'U').charAt(0).toUpperCase() }}
          </div>
          <div>
            <h3>{{ auth.displayName || auth.username }}</h3>
            <span class="badge" [ngClass]="'badge-' + (auth.primaryRole | lowercase)">
              Vai trò: {{ auth.primaryRole }}
            </span>
          </div>
        </div>

        <div class="profile-details-grid mt-4">
          <div class="profile-item">
            <span class="item-label">Tên tài khoản (Username):</span>
            <span class="item-val font-mono">{{ auth.username }}</span>
          </div>

          <div class="profile-item">
            <span class="item-label">Hộp thư điện tử (Email):</span>
            <span class="item-val">{{ auth.email || 'Chưa cập nhật' }}</span>
          </div>

          <div class="profile-item">
            <span class="item-label">Mã định danh Keycloak (sub):</span>
            <span class="item-val font-mono text-muted">{{ auth.keycloakUserId }}</span>
          </div>

          <div class="profile-item">
            <span class="item-label">Các vai trò được cấp (Realm Roles):</span>
            <span class="item-val">
              <span *ngFor="let r of auth.roles" class="badge badge-active me-1">{{ r }}</span>
            </span>
          </div>

          <div class="profile-item">
            <span class="item-label">Trạng thái hồ sơ ngân hàng:</span>
            <span class="item-val">
              <span class="badge badge-active">{{ auth.profile?.profileStatus || 'ACTIVE' }}</span>
            </span>
          </div>

          <div class="profile-item">
            <span class="item-label">Chi nhánh quản lý:</span>
            <span class="item-val">
              {{ auth.profile?.branch ? auth.profile?.branch?.branchName + ' (' + auth.profile?.branch?.branchCode + ')' : 'Chưa phân bổ' }}
            </span>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .profile-container { max-width: 800px; margin: 0 auto; }
    .page-header { margin-bottom: 24px; }
    .text-muted { color: #6b7280; font-size: 0.95rem; }
    .profile-header { display: flex; align-items: center; gap: 16px; padding-bottom: 20px; border-bottom: 1px solid #e5e7eb; }
    .avatar-circle { width: 64px; height: 64px; border-radius: 50%; background: #0f2b48; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 1.8rem; font-weight: bold; }
    .profile-details-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 18px; }
    .profile-item { display: flex; flex-direction: column; gap: 4px; }
    .item-label { font-size: 0.8rem; color: #6b7280; font-weight: 600; text-transform: uppercase; }
    .item-val { font-size: 1rem; color: #1f2a37; font-weight: 500; }
    .font-mono { font-family: monospace; font-size: 0.9rem; }
    .mt-4 { margin-top: 24px; }
    .me-1 { margin-right: 6px; }
  `]
})
export class ProfileComponent {
  authService = inject(AuthService);
}

