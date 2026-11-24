import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AccountService } from '../../../core/services/account.service';
import { TransferService } from '../../../core/services/transfer.service';
import { AuthService } from '../../../core/auth/auth.service';
import { BankAccount } from '../../../core/models/account.model';
import { TransactionSummary } from '../../../core/models/transfer.model';
import { VndCurrencyPipe } from '../../../shared/pipes/vnd-currency.pipe';
import { MaskAccountPipe } from '../../../shared/pipes/mask-account.pipe';

@Component({
  selector: 'app-customer-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, VndCurrencyPipe, MaskAccountPipe],
  template: `
    <div class="dashboard-container">
      <div class="welcome-header">
        <div>
          <h2>Xin chào, {{ authService.currentAuthState.displayName || authService.currentAuthState.username }}!</h2>
          <p class="text-muted">Chào mừng bạn đến với Ngân hàng Trực tuyến. Dưới đây là tóm tắt tài chính của bạn.</p>
        </div>
        <div class="header-actions">
          <a routerLink="/customer/transfers" class="btn btn-primary">Chuyển tiền</a>
          <a routerLink="/customer/accounts/open" class="btn btn-outline-primary">Mở tài khoản</a>
        </div>
      </div>

      <div *ngIf="loading" class="spinner-container">
        <div class="spinner"></div>
        <p class="mt-3">Đang tải thông tin tài chính...</p>
      </div>

      <div *ngIf="errorMessage" class="alert alert-danger">
        {{ errorMessage }}
      </div>

      <div *ngIf="!loading">
        <!-- Summary Stat Cards -->
        <div class="stat-grid">
          <div class="bank-card stat-card">
            <span class="stat-icon">💰</span>
            <div class="stat-info">
              <span class="stat-label">Tổng số dư khả dụng</span>
              <span class="stat-value text-primary">{{ totalBalance | vnd }}</span>
            </div>
          </div>

          <div class="bank-card stat-card">
            <span class="stat-icon">💳</span>
            <div class="stat-info">
              <span class="stat-label">Số lượng tài khoản</span>
              <span class="stat-value">{{ accounts.length }} tài khoản</span>
            </div>
          </div>

          <div class="bank-card stat-card">
            <span class="stat-icon">🏦</span>
            <div class="stat-info">
              <span class="stat-label">Chi nhánh quản lý</span>
              <span class="stat-value">{{ authService.currentAuthState.profile?.branch?.branchName || 'Trung tâm' }}</span>
            </div>
          </div>
        </div>

        <!-- Accounts Overview -->
        <div class="bank-card">
          <div class="bank-card-header">
            <h3 class="bank-card-title">Danh sách tài khoản của tôi</h3>
            <a routerLink="/customer/accounts" class="btn btn-sm btn-outline-primary">Xem tất cả</a>
          </div>

          <div *ngIf="accounts.length === 0" class="empty-state">
            <div class="empty-state-icon">💳</div>
            <div class="empty-state-title">Chưa có tài khoản nào</div>
            <p>Mở tài khoản thanh toán hoặc tiết kiệm đầu tiên ngay hôm nay.</p>
            <a routerLink="/customer/accounts/open" class="btn btn-primary mt-3">Mở tài khoản ngay</a>
          </div>

          <div *ngIf="accounts.length > 0" class="bank-table-responsive">
            <table class="bank-table">
              <thead>
                <tr>
                  <th>Số tài khoản</th>
                  <th>Loại tài khoản</th>
                  <th>Trạng thái</th>
                  <th>Số dư khả dụng</th>
                  <th>Tiền tệ</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let acc of accounts">
                  <td class="font-mono"><strong>{{ acc.maskedAccountNumber | maskAccount }}</strong></td>
                  <td>{{ acc.accountType === 'CHECKING' ? 'Thanh toán' : 'Tiết kiệm' }}</td>
                  <td>
                    <span class="badge" [ngClass]="'badge-' + (acc.accountStatus | lowercase)">
                      {{ acc.accountStatus }}
                    </span>
                  </td>
                  <td class="font-bold">{{ acc.balance | vnd }}</td>
                  <td>{{ acc.currency }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Recent Transactions -->
        <div class="bank-card">
          <div class="bank-card-header">
            <h3 class="bank-card-title">Giao dịch gần đây</h3>
            <a routerLink="/customer/transactions" class="btn btn-sm btn-outline-primary">Xem toàn bộ lịch sử</a>
          </div>

          <div *ngIf="recentTransactions.length === 0" class="empty-state">
            <div class="empty-state-icon">📝</div>
            <div class="empty-state-title">Chưa có giao dịch phát sinh</div>
            <p>Các giao dịch chuyển tiền đến hoặc đi sẽ xuất hiện tại đây.</p>
          </div>

          <div *ngIf="recentTransactions.length > 0" class="bank-table-responsive">
            <table class="bank-table">
              <thead>
                <tr>
                  <th>Mã giao dịch</th>
                  <th>Loại</th>
                  <th>Tài khoản đối ứng</th>
                  <th>Số tiền</th>
                  <th>Nội dung</th>
                  <th>Thời gian</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let tx of recentTransactions">
                  <td class="font-mono">{{ tx.transactionReference }}</td>
                  <td>
                    <span class="badge" [ngClass]="tx.direction === 'DEBIT' ? 'badge-debit' : 'badge-credit'">
                      {{ tx.direction === 'DEBIT' ? 'TIỀN RA' : 'TIỀN VÀO' }}
                    </span>
                  </td>
                  <td>
                    {{ tx.direction === 'DEBIT' ? (tx.destinationAccountNumber | maskAccount) : (tx.sourceAccountNumber | maskAccount) }}
                  </td>
                  <td class="font-bold" [ngClass]="tx.direction === 'DEBIT' ? 'text-danger' : 'text-success'">
                    {{ tx.direction === 'DEBIT' ? '-' : '+' }}{{ tx.amount | vnd }}
                  </td>
                  <td>{{ tx.description || 'Không có ghi chú' }}</td>
                  <td class="text-muted">{{ tx.timestamp | date:'dd/MM/yyyy HH:mm' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dashboard-container { max-width: 1200px; margin: 0 auto; }
    .welcome-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
    .text-muted { color: #6b7280; font-size: 0.95rem; }
    .header-actions { display: flex; gap: 12px; }
    .stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 20px; margin-bottom: 24px; }
    .stat-card { display: flex; align-items: center; gap: 16px; padding: 20px; margin-bottom: 0; }
    .stat-icon { font-size: 2.2rem; background: #eef2f6; padding: 12px; border-radius: 12px; }
    .stat-info { display: flex; flex-direction: column; }
    .stat-label { font-size: 0.85rem; color: #6b7280; font-weight: 600; text-transform: uppercase; }
    .stat-value { font-size: 1.4rem; font-weight: 700; color: #0f2b48; }
    .font-mono { font-family: monospace; }
    .font-bold { font-weight: 700; }
    .text-primary { color: #1a56db; }
    .text-danger { color: #e02424; }
    .text-success { color: #0e9f6e; }
    .mt-3 { margin-top: 12px; }
  `]
})
export class CustomerDashboardComponent implements OnInit {
  authService = inject(AuthService);
  private accountService = inject(AccountService);
  private transferService = inject(TransferService);

  accounts: BankAccount[] = [];
  recentTransactions: TransactionSummary[] = [];
  totalBalance = 0;
  loading = true;
  errorMessage: string | null = null;

  ngOnInit(): void {
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.loading = true;
    this.errorMessage = null;

    forkJoin({
      accounts: this.accountService.getMyAccounts().pipe(catchError(() => of([]))),
      transactions: this.transferService.getTransactions(0, 5).pipe(catchError(() => of({ content: [] } as any)))
    }).subscribe({
      next: ({ accounts, transactions }) => {
        this.accounts = accounts;
        this.totalBalance = accounts.reduce((sum, acc) => sum + (Number(acc.balance) || 0), 0);
        this.recentTransactions = transactions?.content || [];
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Không thể tải dữ liệu bảng điều khiển. Vui lòng thử lại sau.';
        this.loading = false;
      }
    });
  }
}

