import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AccountService } from '../../../core/services/account.service';
import { BankAccount } from '../../../core/models/account.model';
import { VndCurrencyPipe } from '../../../shared/pipes/vnd-currency.pipe';
import { MaskAccountPipe } from '../../../shared/pipes/mask-account.pipe';

@Component({
  selector: 'app-customer-accounts',
  standalone: true,
  imports: [CommonModule, RouterModule, VndCurrencyPipe, MaskAccountPipe],
  template: `
    <div class="accounts-container">
      <div class="page-header">
        <div>
          <h2>Danh sách tài khoản ngân hàng</h2>
          <p class="text-muted">Quản lý và theo dõi thông tin các tài khoản thanh toán và tiết kiệm của bạn.</p>
        </div>
        <a routerLink="/customer/accounts/open" class="btn btn-primary">
          + Mở tài khoản mới
        </a>
      </div>

      <div *ngIf="loading" class="spinner-container">
        <div class="spinner"></div>
        <p class="mt-3">Đang tải danh sách tài khoản...</p>
      </div>

      <div *ngIf="errorMessage" class="alert alert-danger">
        {{ errorMessage }}
      </div>

      <div *ngIf="!loading">
        <div *ngIf="accounts.length === 0" class="bank-card empty-state">
          <div class="empty-state-icon">💳</div>
          <div class="empty-state-title">Bạn chưa có tài khoản ngân hàng nào</div>
          <p>Mở tài khoản ngay để kích hoạt các tính năng nhận tiền và chuyển tiền trực tuyến.</p>
          <a routerLink="/customer/accounts/open" class="btn btn-primary mt-3">Mở tài khoản</a>
        </div>

        <div *ngIf="accounts.length > 0" class="accounts-grid">
          <div *ngFor="let acc of accounts" class="bank-card account-card">
            <div class="account-card-top">
              <span class="acc-type-pill">{{ acc.accountType === 'CHECKING' ? 'Thanh toán' : 'Tiết kiệm' }}</span>
              <span class="badge" [ngClass]="'badge-' + (acc.accountStatus | lowercase)">
                {{ acc.accountStatus }}
              </span>
            </div>

            <div class="account-number-display">
              <span class="label">Số tài khoản</span>
              <span class="number font-mono">{{ acc.maskedAccountNumber | maskAccount }}</span>
            </div>

            <div class="account-balance-display">
              <span class="label">Số dư khả dụng</span>
              <span class="balance-value text-primary">{{ acc.balance | vnd }}</span>
            </div>

            <div class="account-card-footer">
              <span class="opened-date">Ngày mở: {{ acc.openedAt | date:'dd/MM/yyyy' }}</span>
              <button class="btn btn-sm btn-outline-primary" (click)="viewDetails(acc)">Chi tiết</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Account Details Modal -->
      <div *ngIf="selectedAccount" class="modal-overlay" (click)="closeDetails()">
        <div class="modal-dialog" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3 class="modal-title">Chi tiết tài khoản</h3>
            <button class="modal-close" (click)="closeDetails()">✕</button>
          </div>

          <div class="modal-body">
            <div class="detail-row">
              <span class="detail-label">Số tài khoản:</span>
              <span class="detail-val font-mono">{{ selectedAccount.maskedAccountNumber | maskAccount }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">Loại tài khoản:</span>
              <span class="detail-val">{{ selectedAccount.accountType === 'CHECKING' ? 'Tài khoản thanh toán' : 'Tài khoản tiết kiệm' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">Trạng thái:</span>
              <span class="detail-val">
                <span class="badge" [ngClass]="'badge-' + (selectedAccount.accountStatus | lowercase)">
                  {{ selectedAccount.accountStatus }}
                </span>
              </span>
            </div>
            <div class="detail-row">
              <span class="detail-label">Số dư khả dụng:</span>
              <span class="detail-val font-bold text-primary">{{ selectedAccount.balance | vnd }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">Loại tiền tệ:</span>
              <span class="detail-val">{{ selectedAccount.currency }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">Ngày kích hoạt:</span>
              <span class="detail-val">{{ selectedAccount.openedAt | date:'dd/MM/yyyy HH:mm' }}</span>
            </div>
          </div>

          <div class="modal-footer">
            <a [routerLink]="['/customer/transfers']" [queryParams]="{src: selectedAccount.id}" class="btn btn-primary" (click)="closeDetails()">Chuyển tiền từ tài khoản này</a>
            <button class="btn btn-secondary" (click)="closeDetails()">Đóng</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .accounts-container { max-width: 1200px; margin: 0 auto; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
    .text-muted { color: #6b7280; font-size: 0.95rem; }
    .accounts-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 20px; }
    .account-card { display: flex; flex-direction: column; justify-content: space-between; padding: 22px; margin-bottom: 0; }
    .account-card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .acc-type-pill { background: #e0f2fe; color: #0369a1; font-weight: 600; font-size: 0.78rem; padding: 4px 10px; border-radius: 6px; }
    .account-number-display, .account-balance-display { margin-bottom: 14px; }
    .label { font-size: 0.8rem; color: #6b7280; display: block; margin-bottom: 2px; }
    .number { font-size: 1.15rem; font-weight: 700; color: #0f2b48; }
    .balance-value { font-size: 1.5rem; font-weight: 700; }
    .account-card-footer { display: flex; justify-content: space-between; align-items: center; padding-top: 14px; border-top: 1px solid #edf2f7; }
    .opened-date { font-size: 0.8rem; color: #9ca3af; }
    .detail-row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #f3f4f6; font-size: 0.95rem; }
    .detail-label { color: #6b7280; font-weight: 500; }
    .detail-val { font-weight: 600; color: #1f2a37; }
    .font-mono { font-family: monospace; }
    .font-bold { font-weight: 700; }
    .text-primary { color: #1a56db; }
    .mt-3 { margin-top: 12px; }
  `]
})
export class CustomerAccountsComponent implements OnInit {
  private accountService = inject(AccountService);

  accounts: BankAccount[] = [];
  selectedAccount: BankAccount | null = null;
  loading = true;
  errorMessage: string | null = null;

  ngOnInit(): void {
    this.loadAccounts();
  }

  loadAccounts(): void {
    this.loading = true;
    this.errorMessage = null;

    this.accountService.getMyAccounts().subscribe({
      next: (accounts) => {
        this.accounts = accounts;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Không thể tải danh sách tài khoản. Vui lòng thử lại sau.';
        this.loading = false;
      }
    });
  }

  viewDetails(account: BankAccount): void {
    this.selectedAccount = account;
  }

  closeDetails(): void {
    this.selectedAccount = null;
  }
}

