import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { AccountService } from '../../../core/services/account.service';
import { TransferService } from '../../../core/services/transfer.service';
import { BankAccount } from '../../../core/models/account.model';
import { TransferRequest, TransferResponse } from '../../../core/models/transfer.model';
import { VndCurrencyPipe } from '../../../shared/pipes/vnd-currency.pipe';
import { MaskAccountPipe } from '../../../shared/pipes/mask-account.pipe';

@Component({
  selector: 'app-transfer',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, VndCurrencyPipe, MaskAccountPipe],
  template: `
    <div class="transfer-container">
      <div class="page-header">
        <h2>Chuyển tiền nội bộ trực tuyến</h2>
        <p class="text-muted">Chuyển khoản an toàn, tức thì đến các tài khoản trong hệ thống Ngân hàng.</p>
      </div>

      <!-- Result Screen -->
      <div *ngIf="completedTransfer" class="bank-card success-card">
        <div class="success-icon">✓</div>
        <h3>Giao dịch chuyển tiền thành công!</h3>
        <p class="text-muted">Lệnh chuyển tiền của bạn đã được ghi nhận vào sổ cái ngân hàng.</p>

        <div class="receipt-box">
          <div class="receipt-row">
            <span>Mã tham chiếu:</span>
            <strong class="font-mono">{{ completedTransfer.transactionReference }}</strong>
          </div>
          <div class="receipt-row">
            <span>Số tiền chuyển:</span>
            <strong class="text-primary font-bold">{{ completedTransfer.amount | vnd }}</strong>
          </div>
          <div class="receipt-row">
            <span>Tài khoản trích nợ:</span>
            <span class="font-mono">{{ completedTransfer.sourceAccountNumber | maskAccount }}</span>
          </div>
          <div class="receipt-row">
            <span>Tài khoản thụ hưởng:</span>
            <span class="font-mono">{{ completedTransfer.destinationAccountNumber | maskAccount }}</span>
          </div>
          <div class="receipt-row">
            <span>Nội dung:</span>
            <span>{{ completedTransfer.description || 'Không có ghi chú' }}</span>
          </div>
          <div class="receipt-row">
            <span>Thời gian hoàn tất:</span>
            <span>{{ completedTransfer.completedAt | date:'dd/MM/yyyy HH:mm:ss' }}</span>
          </div>
        </div>

        <div class="receipt-actions">
          <button class="btn btn-primary" (click)="resetForm()">Tạo giao dịch mới</button>
          <a routerLink="/customer/transactions" class="btn btn-secondary">Xem lịch sử giao dịch</a>
        </div>
      </div>

      <!-- Transfer Form -->
      <div *ngIf="!completedTransfer" class="bank-card form-card">
        <div *ngIf="errorMessage" class="alert alert-danger">
          {{ errorMessage }}
        </div>

        <div *ngIf="accountsLoading" class="spinner-container">
          <div class="spinner"></div>
          <p class="mt-3">Đang kiểm tra tài khoản của bạn...</p>
        </div>

        <div *ngIf="!accountsLoading && accounts.length === 0" class="empty-state">
          <div class="empty-state-icon">⚠️</div>
          <div class="empty-state-title">Bạn chưa có tài khoản đang hoạt động</div>
          <p>Cần ít nhất một tài khoản ở trạng thái ACTIVE để thực hiện chuyển tiền.</p>
          <a routerLink="/customer/accounts/open" class="btn btn-primary mt-3">Mở tài khoản</a>
        </div>

        <form *ngIf="!accountsLoading && accounts.length > 0" (ngSubmit)="reviewTransfer()">
          <!-- Source Account -->
          <div class="form-group">
            <label class="form-label" for="sourceAccount">Tài khoản nguồn (Trích tiền) <span class="required">*</span></label>
            <select id="sourceAccount" [(ngModel)]="request.sourceAccountId" (change)="onSourceAccountChange()" name="sourceAccountId" class="form-select" required>
              <option value="" disabled selected>-- Chọn tài khoản chuyển --</option>
              <option *ngFor="let acc of accounts" [value]="acc.id">
                {{ acc.maskedAccountNumber | maskAccount }} (Số dư: {{ acc.balance | vnd }})
              </option>
            </select>
            <span class="form-text" *ngIf="selectedSourceAccount">
              Số dư khả dụng: <strong>{{ selectedSourceAccount.balance | vnd }}</strong>
            </span>
          </div>

          <!-- Destination Account -->
          <div class="form-group">
            <label class="form-label" for="destAccount">Số tài khoản thụ hưởng (Đích đến) <span class="required">*</span></label>
            <input type="text" id="destAccount" [(ngModel)]="request.destinationAccountNumber" name="destinationAccountNumber" class="form-control" placeholder="Nhập số tài khoản người nhận (10-20 số)" required minlength="10" maxlength="20" />
            <span class="form-text">Nhập chính xác số tài khoản ngân hàng nội bộ của người nhận.</span>
          </div>

          <!-- Amount -->
          <div class="form-group">
            <label class="form-label" for="amount">Số tiền chuyển (VND) <span class="required">*</span></label>
            <input type="number" id="amount" [(ngModel)]="request.amount" name="amount" class="form-control" placeholder="Ví dụ: 500000" min="1" step="1000" required />
            <span class="form-text" *ngIf="request.amount">
              Bằng chữ: <strong>{{ request.amount | vnd }}</strong>
            </span>
          </div>

          <!-- Description -->
          <div class="form-group">
            <label class="form-label" for="description">Nội dung chuyển tiền (Tối đa 255 ký tự)</label>
            <input type="text" id="description" [(ngModel)]="request.description" name="description" class="form-control" placeholder="Ghi chú nội dung chuyển tiền..." maxlength="255" />
          </div>

          <div class="form-actions">
            <button type="submit" class="btn btn-primary" [disabled]="!isFormValid() || submitting">
              Tiếp tục xác nhận
            </button>
            <a routerLink="/customer/dashboard" class="btn btn-secondary">Hủy bỏ</a>
          </div>
        </form>
      </div>

      <!-- Confirmation Modal -->
      <div *ngIf="showConfirmModal" class="modal-overlay" (click)="cancelReview()">
        <div class="modal-dialog" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3 class="modal-title">Xác nhận chuyển khoản</h3>
            <button class="modal-close" (click)="cancelReview()">✕</button>
          </div>

          <div class="modal-body">
            <div class="confirm-summary">
              <div class="summary-row">
                <span>Tài khoản trích nợ:</span>
                <strong>{{ selectedSourceAccount?.maskedAccountNumber | maskAccount }}</strong>
              </div>
              <div class="summary-row">
                <span>Tài khoản nhận:</span>
                <strong class="font-mono">{{ request.destinationAccountNumber }}</strong>
              </div>
              <div class="summary-row">
                <span>Số tiền chuyển:</span>
                <strong class="text-primary font-bold">{{ request.amount | vnd }}</strong>
              </div>
              <div class="summary-row">
                <span>Nội dung chuyển tiền:</span>
                <span>{{ request.description || 'Không có ghi chú' }}</span>
              </div>
              <div class="summary-row">
                <span>Phí giao dịch:</span>
                <span class="text-success font-bold">0 ₫ (Miễn phí)</span>
              </div>
            </div>

            <div class="alert alert-warning mt-3">
              ⚠️ Vui lòng kiểm tra kỹ số tài khoản người nhận trước khi hoàn tất chuyển tiền. Giao dịch tài chính là không thể hoàn tác sau khi đã ghi nhận sổ cái.
            </div>
          </div>

          <div class="modal-footer">
            <button class="btn btn-primary" [disabled]="submitting" (click)="executeTransfer()">
              {{ submitting ? 'Đang thực hiện chuyển tiền...' : 'Xác nhận chuyển ngay' }}
            </button>
            <button class="btn btn-secondary" [disabled]="submitting" (click)="cancelReview()">Hủy</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .transfer-container { max-width: 750px; margin: 0 auto; }
    .page-header { margin-bottom: 24px; }
    .text-muted { color: #6b7280; font-size: 0.95rem; }
    .required { color: #e02424; }
    .form-actions { display: flex; gap: 12px; margin-top: 24px; }
    .success-card { text-align: center; padding: 36px 24px; }
    .success-icon { width: 64px; height: 64px; border-radius: 50%; background: #def7ec; color: #03543f; font-size: 32px; font-weight: bold; display: flex; align-items: center; justify-content: center; margin: 0 auto 16px; }
    .receipt-box { background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; text-align: left; max-width: 550px; margin: 24px auto; }
    .receipt-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f3f4f6; font-size: 0.95rem; }
    .receipt-row:last-child { border-bottom: none; }
    .receipt-actions { display: flex; justify-content: center; gap: 14px; margin-top: 20px; }
    .confirm-summary { background: #f9fafb; border-radius: 8px; padding: 16px; border: 1px solid #e5e7eb; }
    .summary-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f3f4f6; }
    .summary-row:last-child { border-bottom: none; }
    .font-mono { font-family: monospace; }
    .font-bold { font-weight: 700; }
    .text-primary { color: #1a56db; }
    .text-success { color: #0e9f6e; }
    .mt-3 { margin-top: 12px; }
  `]
})
export class CustomerTransferComponent implements OnInit {
  private accountService = inject(AccountService);
  private transferService = inject(TransferService);
  private route = inject(ActivatedRoute);

  accounts: BankAccount[] = [];
  selectedSourceAccount: BankAccount | null = null;
  accountsLoading = true;
  submitting = false;

  request: TransferRequest = {
    sourceAccountId: '',
    destinationAccountNumber: '',
    amount: 0,
    description: ''
  };

  /** Current Idempotency-Key. Preserved on retry, regenerated on fresh transfer. */
  currentIdempotencyKey: string = '';

  showConfirmModal = false;
  errorMessage: string | null = null;
  completedTransfer: TransferResponse | null = null;

  ngOnInit(): void {
    this.currentIdempotencyKey = this.transferService.generateIdempotencyKey();
    this.loadAccounts();
  }

  loadAccounts(): void {
    this.accountsLoading = true;
    this.accountService.getMyAccounts().subscribe({
      next: (accs) => {
        // Only ACTIVE accounts can transfer
        this.accounts = accs.filter((a) => a.accountStatus === 'ACTIVE');
        this.accountsLoading = false;

        // Auto select source account if query param passed
        const srcParam = this.route.snapshot.queryParamMap.get('src');
        if (srcParam) {
          const match = this.accounts.find((a) => a.id === srcParam);
          if (match) {
            this.request.sourceAccountId = match.id;
            this.selectedSourceAccount = match;
          }
        }
      },
      error: () => {
        this.accountsLoading = false;
        this.errorMessage = 'Không thể tải danh sách tài khoản nguồn. Vui lòng tải lại trang.';
      }
    });
  }

  onSourceAccountChange(): void {
    this.selectedSourceAccount = this.accounts.find((a) => a.id === this.request.sourceAccountId) || null;
  }

  isFormValid(): boolean {
    if (!this.request.sourceAccountId || !this.request.destinationAccountNumber) {
      return false;
    }
    if (!this.request.amount || this.request.amount <= 0) {
      return false;
    }
    if (this.selectedSourceAccount && this.request.amount > Number(this.selectedSourceAccount.balance)) {
      return false;
    }
    return true;
  }

  reviewTransfer(): void {
    this.errorMessage = null;
    if (!this.selectedSourceAccount) {
      this.errorMessage = 'Vui lòng chọn tài khoản nguồn.';
      return;
    }
    if (this.request.amount > Number(this.selectedSourceAccount.balance)) {
      this.errorMessage = 'Số dư tài khoản không đủ để thực hiện giao dịch này.';
      return;
    }
    this.showConfirmModal = true;
  }

  cancelReview(): void {
    this.showConfirmModal = false;
  }

  executeTransfer(): void {
    this.submitting = true;
    this.errorMessage = null;

    // Call transfer endpoint with preserved Idempotency-Key
    this.transferService.executeTransfer(this.request, this.currentIdempotencyKey).subscribe({
      next: (response) => {
        this.submitting = false;
        this.showConfirmModal = false;
        this.completedTransfer = response;
      },
      error: (err) => {
        this.submitting = false;
        this.showConfirmModal = false;

        if (err.status === 409) {
          this.errorMessage = 'Xung đột khóa giao dịch (Idempotency Key Conflict): Khóa gửi yêu cầu đã bị trùng lặp với dữ liệu khác.';
        } else if (err.status === 400) {
          this.errorMessage = err?.error?.message || 'Yêu cầu chuyển tiền không hợp lệ hoặc số dư không đủ.';
        } else if (err.status === 403) {
          this.errorMessage = 'Bạn không có quyền chuyển tiền từ tài khoản này hoặc tài khoản đã bị khóa.';
        } else {
          // Keep currentIdempotencyKey intact so the user can safely retry uncertain network errors
          this.errorMessage = err?.error?.message || 'Không thể kết nối đến máy chủ. Vui lòng thử lại với cùng mã giao dịch này.';
        }
      }
    });
  }

  resetForm(): void {
    this.completedTransfer = null;
    this.currentIdempotencyKey = this.transferService.generateIdempotencyKey();
    this.request = {
      sourceAccountId: '',
      destinationAccountNumber: '',
      amount: 0,
      description: ''
    };
    this.selectedSourceAccount = null;
    this.loadAccounts();
  }
}

