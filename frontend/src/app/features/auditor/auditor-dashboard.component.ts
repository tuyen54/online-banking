import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TransferService } from '../../core/services/transfer.service';
import { AuditService } from '../../core/services/audit.service';
import { TransactionSummary, Page } from '../../core/models/transfer.model';
import { AuditLog } from '../../core/models/audit.model';
import { VndCurrencyPipe } from '../../shared/pipes/vnd-currency.pipe';
import { MaskAccountPipe } from '../../shared/pipes/mask-account.pipe';

@Component({
  selector: 'app-auditor-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, VndCurrencyPipe, MaskAccountPipe],
  template: `
    <div class="auditor-container">
      <div class="page-header">
        <div>
          <h2>Bảng điều khiển Kiểm toán viên (AUDITOR)</h2>
          <p class="text-muted">Giám sát độc lập, chỉ đọc toàn bộ các giao dịch tài chính và sự kiện bảo mật hệ thống.</p>
        </div>
        <div class="badge badge-locked">CHẾ ĐỘ CHỈ ĐỌC (READ-ONLY)</div>
      </div>

      <div class="tabs-header">
        <button class="tab-btn" [class.active]="activeTab === 'tx'" (click)="activeTab = 'tx'">
          Toàn bộ giao dịch ngân hàng
        </button>
        <button class="tab-btn" [class.active]="activeTab === 'audit'" (click)="activeTab = 'audit'">
          Nhật ký kiểm toán bảo mật (Audit Logs)
        </button>
      </div>

      <!-- Tab 1: Transactions -->
      <div *ngIf="activeTab === 'tx'" class="bank-card tab-content">
        <div class="bank-card-header">
          <h3 class="bank-card-title">Sổ cái giao dịch tài chính toàn hệ thống</h3>
        </div>

        <div *ngIf="txLoading" class="spinner-container">
          <div class="spinner"></div>
          <p class="mt-3">Đang truy xuất dữ liệu sổ cái...</p>
        </div>

        <div *ngIf="!txLoading && transactions.length === 0" class="empty-state">
          <p>Không có giao dịch nào trong hệ thống.</p>
        </div>

        <div *ngIf="!txLoading && transactions.length > 0">
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
                <tr *ngFor="let tx of transactions">
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
            <span class="pagination-info">Trang {{ pageData.number + 1 }} / {{ pageData.totalPages }} (Tổng: {{ pageData.totalElements }} giao dịch)</span>
            <div class="pagination-buttons">
              <button class="btn btn-sm btn-secondary" [disabled]="pageData.first" (click)="loadTransactions(currentPage - 1)">‹ Trước</button>
              <button class="btn btn-sm btn-secondary" [disabled]="pageData.last" (click)="loadTransactions(currentPage + 1)">Sau ›</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Tab 2: Audit Logs -->
      <div *ngIf="activeTab === 'audit'" class="bank-card tab-content">
        <div class="bank-card-header">
          <h3 class="bank-card-title">Nhật ký truy vết bảo mật (Security Trail)</h3>
          <button class="btn btn-sm btn-outline-primary" (click)="loadAuditLogs()">Làm mới</button>
        </div>

        <div *ngIf="auditLoading" class="spinner-container">
          <div class="spinner"></div>
          <p class="mt-3">Đang truy vấn nhật ký kiểm toán...</p>
        </div>

        <div *ngIf="!auditLoading && auditLogs.length === 0" class="empty-state">
          <p>Chưa có bản ghi nhật ký kiểm toán nào.</p>
        </div>

        <div *ngIf="!auditLoading && auditLogs.length > 0">
          <div class="bank-table-responsive">
            <table class="bank-table">
              <thead>
                <tr>
                  <th>Loại sự kiện</th>
                  <th>Mã định danh (sub)</th>
                  <th>Tên người dùng</th>
                  <th>Phương thức</th>
                  <th>Đường dẫn</th>
                  <th>Mã phản hồi</th>
                  <th>Mô tả chi tiết</th>
                  <th>Thời gian</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let log of auditLogs">
                  <td><span class="badge badge-pending">{{ log.eventType }}</span></td>
                  <td class="font-mono text-muted">{{ log.userId ? (log.userId | slice:0:8) + '...' : 'N/A' }}</td>
                  <td><strong>{{ log.username || 'Ẩn danh' }}</strong></td>
                  <td><span class="badge" [ngClass]="log.httpMethod === 'POST' ? 'badge-credit' : 'badge-frozen'">{{ log.httpMethod }}</span></td>
                  <td class="font-mono">{{ log.requestUri }}</td>
                  <td>
                    <span class="badge" [ngClass]="log.statusCode < 400 ? 'badge-active' : 'badge-locked'">
                      {{ log.statusCode }}
                    </span>
                  </td>
                  <td>{{ log.description }}</td>
                  <td class="text-muted">{{ log.timestamp | date:'dd/MM/yyyy HH:mm:ss' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .auditor-container { max-width: 1300px; margin: 0 auto; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
    .tabs-header { display: flex; gap: 10px; margin-bottom: 20px; border-bottom: 2px solid #e5e7eb; }
    .tab-btn { background: none; border: none; padding: 12px 20px; font-weight: 600; color: #6b7280; font-size: 0.95rem; cursor: pointer; border-bottom: 3px solid transparent; margin-bottom: -2px; }
    .tab-btn.active { color: #805ad5; border-bottom-color: #805ad5; }
    .tab-content { border-top-left-radius: 0; }
    .pagination-bar { display: flex; justify-content: space-between; align-items: center; padding-top: 14px; border-top: 1px solid #edf2f7; margin-top: 14px; }
    .pagination-buttons { display: flex; gap: 8px; }
    .font-mono { font-family: monospace; font-size: 0.85rem; }
    .font-bold { font-weight: 700; }
    .text-primary { color: #1a56db; }
  `]
})
export class AuditorDashboardComponent implements OnInit {
  private transferService = inject(TransferService);
  private auditService = inject(AuditService);

  activeTab: 'tx' | 'audit' = 'tx';

  transactions: TransactionSummary[] = [];
  pageData: Page<TransactionSummary> | null = null;
  currentPage = 0;
  txLoading = true;

  auditLogs: AuditLog[] = [];
  auditLoading = false;

  ngOnInit(): void {
    this.loadTransactions(0);
    this.loadAuditLogs();
  }

  loadTransactions(page: number): void {
    this.txLoading = true;
    this.currentPage = page;
    this.transferService.getTransactions(page, 15).subscribe({
      next: (res) => {
        this.pageData = res;
        this.transactions = res.content;
        this.txLoading = false;
      },
      error: () => {
        this.txLoading = false;
      }
    });
  }

  loadAuditLogs(): void {
    this.auditLoading = true;
    this.auditService.getAuditLogs().subscribe({
      next: (logs) => {
        this.auditLogs = logs;
        this.auditLoading = false;
      },
      error: () => {
        this.auditLoading = false;
      }
    });
  }
}

