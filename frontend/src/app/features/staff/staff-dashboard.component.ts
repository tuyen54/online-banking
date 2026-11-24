import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UserService } from '../../core/services/user.service';
import { TransferService } from '../../core/services/transfer.service';
import { AuthService } from '../../core/auth/auth.service';
import { CustomerProfile } from '../../core/models/user.model';
import { TransactionSummary, Page } from '../../core/models/transfer.model';
import { VndCurrencyPipe } from '../../shared/pipes/vnd-currency.pipe';
import { MaskAccountPipe } from '../../shared/pipes/mask-account.pipe';

@Component({
  selector: 'app-staff-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, VndCurrencyPipe, MaskAccountPipe],
  template: `
    <div class="staff-container">
      <div class="page-header">
        <div>
          <h2>Bảng điều khiển Nhân viên Chi nhánh (STAFF)</h2>
          <p class="text-muted">Tra cứu khách hàng và giám sát giao dịch thuộc phạm vi chi nhánh được phân công.</p>
        </div>
        <div class="branch-badge-box" *ngIf="authService.currentAuthState.profile?.branch as b">
          <span class="label">Chi nhánh:</span>
          <strong>{{ b.branchName }} ({{ b.branchCode }})</strong>
          <span class="badge badge-active">{{ b.status }}</span>
        </div>
      </div>

      <!-- Customer Lookup Card -->
      <div class="bank-card">
        <div class="bank-card-header">
          <h3 class="bank-card-title">🔍 Tra cứu thông tin khách hàng theo mã hồ sơ</h3>
        </div>

        <form (ngSubmit)="searchCustomer()" class="lookup-form">
          <div class="form-group flex-1">
            <label class="form-label" for="profileId">Mã định danh hồ sơ khách hàng (UUID)</label>
            <input type="text" id="profileId" [(ngModel)]="searchId" name="profileId" class="form-control" placeholder="Nhập UUID hồ sơ (ví dụ: e3b0c442-98fc-1c14-9af0-2a3b4c5d6e7f)" required />
            <span class="form-text">Chính sách ABAC: Bạn chỉ có quyền truy cập hồ sơ khách hàng cùng thuộc chi nhánh của bạn.</span>
          </div>
          <button type="submit" class="btn btn-primary" [disabled]="!searchId || searching">
            {{ searching ? 'Đang kiểm tra...' : 'Tra cứu hồ sơ' }}
          </button>
        </form>

        <div *ngIf="lookupError" class="alert alert-danger mt-3">
          {{ lookupError }}
        </div>

        <!-- Customer Profile Result -->
        <div *ngIf="foundCustomer" class="customer-result-box mt-3">
          <h4>Thông tin khách hàng hợp lệ</h4>
          <div class="result-grid">
            <div class="result-item">
              <span class="item-label">Tên tài khoản:</span>
              <strong>{{ foundCustomer.username }}</strong>
            </div>
            <div class="result-item">
              <span class="item-label">Họ và tên:</span>
              <strong>{{ foundCustomer.fullName }}</strong>
            </div>
            <div class="result-item">
              <span class="item-label">Email:</span>
              <span>{{ foundCustomer.email }}</span>
            </div>
            <div class="result-item">
              <span class="item-label">Trạng thái hồ sơ:</span>
              <span class="badge badge-active">{{ foundCustomer.profileStatus }}</span>
            </div>
            <div class="result-item">
              <span class="item-label">Chi nhánh quản lý:</span>
              <span>{{ foundCustomer.branchName }} ({{ foundCustomer.branchCode }})</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Branch Transactions -->
      <div class="bank-card">
        <div class="bank-card-header">
          <h3 class="bank-card-title">📋 Lịch sử giao dịch trong chi nhánh</h3>
        </div>

        <div *ngIf="txLoading" class="spinner-container">
          <div class="spinner"></div>
          <p class="mt-3">Đang tải danh sách giao dịch chi nhánh...</p>
        </div>

        <div *ngIf="!txLoading && branchTransactions.length === 0" class="empty-state">
          <div class="empty-state-icon">🏦</div>
          <div class="empty-state-title">Chưa có giao dịch chi nhánh</div>
          <p>Không có giao dịch phát sinh nào liên quan đến tài khoản thuộc chi nhánh này.</p>
        </div>

        <div *ngIf="!txLoading && branchTransactions.length > 0">
          <div class="bank-table-responsive">
            <table class="bank-table">
              <thead>
                <tr>
                  <th>Mã tham chiếu</th>
                  <th>Số tiền</th>
                  <th>Tài khoản trích nợ</th>
                  <th>Tài khoản thụ hưởng</th>
                  <th>Nội dung</th>
                  <th>Thời gian</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let tx of branchTransactions">
                  <td class="font-mono"><strong>{{ tx.transactionReference }}</strong></td>
                  <td class="font-bold text-primary">{{ tx.amount | vnd }}</td>
                  <td class="font-mono">{{ tx.sourceAccountNumber | maskAccount }}</td>
                  <td class="font-mono">{{ tx.destinationAccountNumber | maskAccount }}</td>
                  <td>{{ tx.description || 'Không có ghi chú' }}</td>
                  <td class="text-muted">{{ tx.timestamp | date:'dd/MM/yyyy HH:mm:ss' }}</td>
                  <td><span class="badge badge-active">{{ tx.status }}</span></td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="pagination-bar" *ngIf="pageData && pageData.totalPages > 1">
            <span class="pagination-info">Trang {{ pageData.number + 1 }} / {{ pageData.totalPages }}</span>
            <div class="pagination-buttons">
              <button class="btn btn-sm btn-secondary" [disabled]="pageData.first" (click)="loadBranchTransactions(currentPage - 1)">‹ Trước</button>
              <button class="btn btn-sm btn-secondary" [disabled]="pageData.last" (click)="loadBranchTransactions(currentPage + 1)">Sau ›</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .staff-container { max-width: 1200px; margin: 0 auto; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
    .branch-badge-box { background: #fff; padding: 10px 16px; border-radius: 8px; border: 1px solid #e2e8f0; display: flex; align-items: center; gap: 10px; }
    .lookup-form { display: flex; gap: 14px; align-items: flex-start; }
    .flex-1 { flex: 1; margin-bottom: 0; }
    .customer-result-box { background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 8px; padding: 20px; }
    .result-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; margin-top: 12px; }
    .result-item { display: flex; flex-direction: column; gap: 4px; font-size: 0.95rem; }
    .item-label { font-size: 0.8rem; color: #4b5563; }
    .pagination-bar { display: flex; justify-content: space-between; align-items: center; padding-top: 14px; border-top: 1px solid #edf2f7; margin-top: 14px; }
    .pagination-buttons { display: flex; gap: 8px; }
    .mt-3 { margin-top: 14px; }
    .font-mono { font-family: monospace; }
    .font-bold { font-weight: 700; }
    .text-primary { color: #1a56db; }
  `]
})
export class StaffDashboardComponent implements OnInit {
  authService = inject(AuthService);
  private userService = inject(UserService);
  private transferService = inject(TransferService);

  searchId = '';
  searching = false;
  lookupError: string | null = null;
  foundCustomer: CustomerProfile | null = null;

  branchTransactions: TransactionSummary[] = [];
  pageData: Page<TransactionSummary> | null = null;
  currentPage = 0;
  txLoading = true;

  ngOnInit(): void {
    this.loadBranchTransactions(0);
  }

  searchCustomer(): void {
    if (!this.searchId.trim()) return;
    this.searching = true;
    this.lookupError = null;
    this.foundCustomer = null;

    this.userService.getCustomerProfile(this.searchId.trim()).subscribe({
      next: (profile) => {
        this.foundCustomer = profile;
        this.searching = false;
      },
      error: (err) => {
        this.searching = false;
        if (err.status === 403) {
          this.lookupError = 'Truy cập bị từ chối (403): Khách hàng không thuộc chi nhánh của bạn hoặc chi nhánh/hồ sơ không hoạt động (Chính sách ABAC).';
        } else if (err.status === 404) {
          this.lookupError = 'Không tìm thấy hồ sơ khách hàng với mã đã cung cấp.';
        } else {
          this.lookupError = err?.error?.message || 'Có lỗi xảy ra trong quá trình tra cứu.';
        }
      }
    });
  }

  loadBranchTransactions(page: number): void {
    this.txLoading = true;
    this.currentPage = page;
    this.transferService.getTransactions(page, 10).subscribe({
      next: (res) => {
        this.pageData = res;
        this.branchTransactions = res.content;
        this.txLoading = false;
      },
      error: () => {
        this.txLoading = false;
      }
    });
  }
}

