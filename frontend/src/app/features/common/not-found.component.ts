import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="notfound-container">
      <div class="bank-card text-center">
        <div class="error-code">404</div>
        <h2>Không tìm thấy trang yêu cầu</h2>
        <p class="text-muted">
          Đường dẫn bạn vừa truy cập không tồn tại hoặc đã được di chuyển.
        </p>
        <div class="mt-4">
          <a routerLink="/" class="btn btn-primary">Quay về trang chủ</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .notfound-container { max-width: 600px; margin: 60px auto; text-align: center; }
    .error-code { font-size: 5rem; font-weight: 800; color: #1a56db; line-height: 1; margin-bottom: 12px; }
    .text-muted { color: #6b7280; font-size: 1rem; }
    .mt-4 { margin-top: 24px; }
  `]
})
export class NotFoundComponent {}

