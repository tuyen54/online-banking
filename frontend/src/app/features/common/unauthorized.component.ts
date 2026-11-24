import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-unauthorized',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="unauthorized-container">
      <div class="bank-card text-center">
        <div class="error-code">403</div>
        <h2>Truy cập bị từ chối (Access Denied)</h2>
        <p class="text-muted">
          Bạn không có quyền truy cập vào tài nguyên hoặc nghiệp vụ ngân hàng này theo chính sách phân quyền vai trò (RBAC) và kiểm soát thuộc tính (ABAC).
        </p>
        <div class="mt-4">
          <a routerLink="/" class="btn btn-primary">Quay về trang chủ</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .unauthorized-container { max-width: 600px; margin: 60px auto; text-align: center; }
    .error-code { font-size: 5rem; font-weight: 800; color: #e02424; line-height: 1; margin-bottom: 12px; }
    .text-muted { color: #6b7280; font-size: 1rem; }
    .mt-4 { margin-top: 24px; }
  `]
})
export class UnauthorizedComponent {}

