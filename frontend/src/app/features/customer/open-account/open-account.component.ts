import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AccountService } from '../../../core/services/account.service';
import { AccountCreationRequest } from '../../../core/models/account.model';

@Component({
  selector: 'app-open-account',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="open-account-container">
      <div class="page-header">
        <h2>Mở tài khoản ngân hàng trực tuyến</h2>
        <p class="text-muted">Chọn loại tài khoản bạn mong muốn mở. Hệ thống sẽ tự động tạo lập tài khoản mới an toàn.</p>
      </div>

      <div class="bank-card form-card">
        <div *ngIf="errorMessage" class="alert alert-danger">
          {{ errorMessage }}
        </div>

        <div *ngIf="successMessage" class="alert alert-success">
          {{ successMessage }}
        </div>

        <div *ngIf="!successMessage">
          <div class="alert alert-info">
            ℹ️ <strong>Quy định mở tài khoản:</strong> Theo chính sách ngân hàng, tài khoản mở mới sẽ có số dư ban đầu là <strong>0 ₫</strong>. Số tài khoản được hệ thống sinh ngẫu nhiên mã hóa an toàn.
          </div>

          <form (ngSubmit)="confirmOpen()">
            <div class="form-group">
              <label class="form-label" for="accountType">Loại tài khoản ngân hàng <span class="required">*</span></label>
              <select id="accountType" [(ngModel)]="request.accountType" name="accountType" class="form-select" required>
                <option value="CHECKING">Tài khoản Thanh toán (CHECKING) - Dùng chi tiêu, chuyển tiền hàng ngày</option>
                <option value="SAVINGS">Tài khoản Tiết kiệm (SAVINGS) - Dùng tích lũy sinh lời</option>
              </select>
            </div>

            <div class="form-group">
              <label class="form-label">Loại tiền tệ</label>
              <input type="text" [value]="request.currency" class="form-control" disabled />
              <span class="form-text">Hiện tại hệ thống chỉ hỗ trợ đơn vị tiền tệ Việt Nam Đồng (VND).</span>
            </div>

            <div class="form-actions">
              <button type="submit" class="btn btn-primary" [disabled]="submitting">
                {{ submitting ? 'Đang khởi tạo tài khoản...' : 'Tiếp tục' }}
              </button>
              <a routerLink="/customer/accounts" class="btn btn-secondary">Hủy bỏ</a>
            </div>
          </form>
        </div>
      </div>

      <!-- Confirmation Modal -->
      <div *ngIf="showConfirmModal" class="modal-overlay" (click)="cancelConfirm()">
        <div class="modal-dialog" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3 class="modal-title">Xác nhận mở tài khoản</h3>
            <button class="modal-close" (click)="cancelConfirm()">✕</button>
          </div>

          <div class="modal-body">
            <p>Vui lòng xác nhận yêu cầu mở tài khoản mới với các thông tin sau:</p>
            <div class="confirm-summary">
              <div class="summary-row">
                <span>Loại tài khoản:</span>
                <strong>{{ request.accountType === 'CHECKING' ? 'Tài khoản Thanh toán' : 'Tài khoản Tiết kiệm' }}</strong>
              </div>
              <div class="summary-row">
                <span>Loại tiền tệ:</span>
                <strong>{{ request.currency }}</strong>
              </div>
              <div class="summary-row">
                <span>Số dư ban đầu:</span>
                <strong>0 ₫</strong>
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button class="btn btn-primary" [disabled]="submitting" (click)="executeOpen()">
              {{ submitting ? 'Đang xử lý...' : 'Xác nhận mở tài khoản' }}
            </button>
            <button class="btn btn-secondary" [disabled]="submitting" (click)="cancelConfirm()">Quay lại</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .open-account-container { max-width: 700px; margin: 0 auto; }
    .page-header { margin-bottom: 24px; }
    .text-muted { color: #6b7280; font-size: 0.95rem; }
    .required { color: #e02424; }
    .form-actions { display: flex; gap: 12px; margin-top: 24px; }
    .confirm-summary { background: #f9fafb; border-radius: 8px; padding: 16px; margin-top: 14px; border: 1px solid #e5e7eb; }
    .summary-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f3f4f6; }
    .summary-row:last-child { border-bottom: none; }
  `]
})
export class OpenAccountComponent {
  private accountService = inject(AccountService);
  private router = inject(Router);

  request: AccountCreationRequest = {
    accountType: 'CHECKING',
    currency: 'VND'
  };

  showConfirmModal = false;
  submitting = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  confirmOpen(): void {
    this.errorMessage = null;
    this.showConfirmModal = true;
  }

  cancelConfirm(): void {
    this.showConfirmModal = false;
  }

  executeOpen(): void {
    this.submitting = true;
    this.errorMessage = null;

    this.accountService.openAccount(this.request).subscribe({
      next: (createdAccount) => {
        this.submitting = false;
        this.showConfirmModal = false;
        this.successMessage = `Mở tài khoản thành công! Số tài khoản của bạn: ${createdAccount.maskedAccountNumber}. Đang chuyển hướng...`;
        setTimeout(() => {
          this.router.navigate(['/customer/accounts']);
        }, 2000);
      },
      error: (err) => {
        this.submitting = false;
        this.showConfirmModal = false;
        this.errorMessage = err?.error?.message || 'Có lỗi xảy ra khi tạo tài khoản. Vui lòng thử lại sau.';
      }
    });
  }
}

