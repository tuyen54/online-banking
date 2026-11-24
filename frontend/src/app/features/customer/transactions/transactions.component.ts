import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { TransferService } from '../../../core/services/transfer.service';
import { TransactionSummary, Page } from '../../../core/models/transfer.model';
import { VndCurrencyPipe } from '../../../shared/pipes/vnd-currency.pipe';
import { MaskAccountPipe } from '../../../shared/pipes/mask-account.pipe';

@Component({
  selector: 'app-customer-transactions',
  standalone: true,
  imports: [CommonModule, RouterModule, VndCurrencyPipe, MaskAccountPipe],
  template: `
    <div class="transactions-container">
      <div class="page-header">
        <div>
          <h2>Lịch sử giao dịch tài chính</h2>
          <p class="text-muted">Theo dõi toàn bộ các giao dịch chuyển tiền đến và đi trong tài khoản của bạn.</p>
        </div>
      </div>

      <div class="bank-card">
        <div *ngIf="loading" class="spinner-container">
          <div class="spinner"></div>
          <p class="mt-3">Đang tải lịch sử giao dịch...</p>
        </div>

        <div *ngIf="errorMessage" class="alert alert-danger">
          {{ errorMessage }}
        </div>

        <div *ngIf="!loading && transactions.length === 0" class="empty-state">
          <div class="empty-state-icon">📝</div>
          <div class="empty-state-title">Chưa có giao dịch nào</div>
          <p>Tài khoản của bạn chưa phát sinh bất kỳ giao dịch chuyển tiền nào.</p>
          <a routerLink="/customer/transfers" class="btn btn-primary mt-3">Chuyển tiền ngay</a>
        </div>

        <div *ngIf="!loading && transactions.length > 0">
          <div class="bank-table-responsive">
            <table class="bank-table">
              <thead>
                <tr>
                  <th>Mã tham chiếu</th>
                  <th>Phân loại</th>
                  <th>Tài khoản nguồn</th>
                  <th>Tài khoản thụ hưởng</th>
                  <th>Số tiền</th>
                  <th>Nội dung</th>
                  <th>Thời gian</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let tx of transactions">
                  <td class="font-mono"><strong>{{ tx.transactionReference }}</strong></td>
                  <td>
                    <span class="badge" [ngClass]="tx.direction === 'DEBIT' ? 'badge-debit' : 'badge-credit'">
                      {{ tx.direction === 'DEBIT' ? 'TIỀN RA' : 'TIỀN VÀO' }}
                    </span>
                  </td>
                  <td class="font-mono">{{ tx.sourceAccountNumber | maskAccount }}</td>
                  <td class="font-mono">{{ tx.destinationAccountNumber | maskAccount }}</td>
                  <td class="font-bold" [ngClass]="tx.direction === 'DEBIT' ? 'text-danger' : 'text-success'">
                    {{ tx.direction === 'DEBIT' ? '-' : '+' }}{{ tx.amount | vnd }}
                  </td>
                  <td>{{ tx.description || 'Không có ghi chú' }}</td>
                  <td class="text-muted">{{ tx.timestamp | date:'dd/MM/yyyy HH:mm:ss' }}</td>
                  <td>
                    <span class="badge badge-active">{{ tx.status }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Pagination Controls -->
          <div class="pagination-bar" *ngIf="pageData && pageData.totalPages > 1">
            <span class="pagination-info">
              Trang {{ pageData.number + 1 }} / {{ pageData.totalPages }} (Tổng số {{ pageData.totalElements }} giao dịch)
            </span>
            <div class="pagination-buttons">
              <button class="btn btn-sm btn-secondary" [disabled]="pageData.first" (click)="goToPage(currentPage - 1)">
                ‹ Trang trước
              </button>
              <button class="btn btn-sm btn-secondary" [disabled]="pageData.last" (click)="goToPage(currentPage + 1)">
                Trang sau ›
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .transactions-container { max-width: 1200px; margin: 0 auto; }
    .page-header { margin-bottom: 24px; }
    .text-muted { color: #6b7280; font-size: 0.95rem; }
    .font-mono { font-family: monospace; }
    .font-bold { font-weight: 700; }
    .text-danger { color: #e02424; }
    .text-success { color: #0e9f6e; }
    .pagination-bar { display: flex; justify-content: space-between; align-items: center; padding: 16px 8px 0; margin-top: 16px; border-top: 1px solid #edf2f7; flex-wrap: wrap; gap: 12px; }
    .pagination-info { font-size: 0.88rem; color: #6b7280; }
    .pagination-buttons { display: flex; gap: 8px; }
    .mt-3 { margin-top: 12px; }
  `]
})
export class CustomerTransactionsComponent implements OnInit {
  private transferService = inject(TransferService);

  transactions: TransactionSummary[] = [];
  pageData: Page<TransactionSummary> | null = null;
  currentPage = 0;
  pageSize = 10;
  loading = true;
  errorMessage: string | null = null;

  ngOnInit(): void {
    this.loadTransactions(0);
  }

  loadTransactions(page: number): void {
    this.loading = true;
    this.errorMessage = null;
    this.currentPage = page;

    this.transferService.getTransactions(page, this.pageSize).subscribe({
      next: (pageResult) => {
        this.pageData = pageResult;
        this.transactions = pageResult.content;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Không thể tải lịch sử giao dịch. Vui lòng thử lại sau.';
      }
    });
  }

  goToPage(page: number): void {
    if (page >= 0 && this.pageData && page < this.pageData.totalPages) {
      this.loadTransactions(page);
    }
  }
}

