import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../core/services/admin.service';
import { BranchService } from '../../core/services/branch.service';
import { AuditService } from '../../core/services/audit.service';
import { AdminAccountResponse } from '../../core/models/account.model';
import { Branch } from '../../core/models/user.model';
import { AuditLog } from '../../core/models/audit.model';
import { Page } from '../../core/models/transfer.model';
import { MaskAccountPipe } from '../../shared/pipes/mask-account.pipe';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, MaskAccountPipe],
  template: `
    <div class="admin-container">
      <div class="page-header">
        <div>
          <h2>Trung tâm Quản trị Hệ thống (ADMIN)</h2>
          <p class="text-muted">Quản lý vòng đời tài khoản ngân hàng, phân bổ chi nhánh và giám sát bảo mật.</p>
        </div>
        <div class="badge badge-locked">QUẢN TRỊ AN TOÀN - KHÔNG TRUY CẬP SỐ DƯ/GIAO DỊCH</div>
      </div>

      <div class="tabs-header">
        <button class="tab-btn" [class.active]="activeTab === 'accounts'" (click)="activeTab = 'accounts'">
          Quản lý tài khoản (Khóa/Mở khóa)
        </button>
        <button class="tab-btn" [class.active]="activeTab === 'branches'" (click)="activeTab = 'branches'">
          Phân bổ chi nhánh
        </button>
        <button class="tab-btn" [class.active]="activeTab === 'audit'" (click)="activeTab = 'audit'">
          Nhật ký bảo mật
        </button>
      </div>

      <!-- Tab 1: Account Management -->
      <div *ngIf="activeTab === 'accounts'" class="bank-card tab-content">
        <div class="bank-card-header">
          <h3 class="bank-card-title">Danh sách tài khoản ngân hàng (Không hiển thị số dư)</h3>
          <div class="filter-box">
            <select [(ngModel)]="statusFilter" (change)="loadAccounts(0)" class="form-select form-select-sm">
              <option value="">-- Tất cả trạng thái --</option>
              <option value="ACTIVE">ACTIVE (Đang hoạt động)</option>
              <option value="LOCKED">LOCKED (Đã khóa)</option>
              <option value="FROZEN">FROZEN (Đóng băng)</option>
              <option value="CLOSED">CLOSED (Đã đóng)</option>
            </select>
          </div>
        </div>

        <div *ngIf="actionSuccess" class="alert alert-success">
          {{ actionSuccess }}
        </div>
        <div *ngIf="actionError" class="alert alert-danger">
          {{ actionError }}
        </div>

        <div *ngIf="accLoading" class="spinner-container">
          <div class="spinner"></div>
          <p class="mt-3">Đang tải danh sách tài khoản...</p>
        </div>

        <div *ngIf="!accLoading && accounts.length === 0" class="empty-state">
          <p>Không tìm thấy tài khoản nào phù hợp.</p>
        </div>

        <div *ngIf="!accLoading && accounts.length > 0">
          <div class="bank-table-responsive">
            <table class="bank-table">
              <thead>
                <tr>
                  <th>Số tài khoản</th>
                  <th>Chủ tài khoản</th>
                  <th>Loại</th>
                  <th>Trạng thái</th>
                  <th>Chi nhánh</th>
                  <th>Ngày tạo</th>
                  <th>Hành động</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let acc of accounts">
                  <td class="font-mono"><strong>{{ acc.maskedAccountNumber | maskAccount }}</strong></td>
                  <td>{{ acc.ownerUsername }}</td>
                  <td>{{ acc.accountType }}</td>
                  <td>
                    <span class="badge" [ngClass]="'badge-' + (acc.accountStatus | lowercase)">
                      {{ acc.accountStatus }}
                    </span>
                  </td>
                  <td>{{ acc.ownerBranchCode || 'Chưa gán' }}</td>
                  <td class="text-muted">{{ acc.openedAt | date:'dd/MM/yyyy' }}</td>
                  <td>
                    <button *ngIf="acc.accountStatus === 'ACTIVE'" class="btn btn-sm btn-danger" (click)="openStatusModal(acc, 'LOCK')">
                      Khóa tài khoản
                    </button>
                    <button *ngIf="acc.accountStatus === 'LOCKED'" class="btn btn-sm btn-success" (click)="openStatusModal(acc, 'UNLOCK')">
                      Mở khóa
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="pagination-bar" *ngIf="pageData && pageData.totalPages > 1">
            <span class="pagination-info">Trang {{ pageData.number + 1 }} / {{ pageData.totalPages }}</span>
            <div class="pagination-buttons">
              <button class="btn btn-sm btn-secondary" [disabled]="pageData.first" (click)="loadAccounts(currentPage - 1)">‹ Trước</button>
              <button class="btn btn-sm btn-secondary" [disabled]="pageData.last" (click)="loadAccounts(currentPage + 1)">Sau ›</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Tab 2: Branch Assignment -->
      <div *ngIf="activeTab === 'branches'" class="bank-card tab-content">
        <div class="bank-card-header">
          <h3 class="bank-card-title">Phân bổ chi nhánh cho người dùng</h3>
        </div>

        <form (ngSubmit)="executeBranchAssignment()" class="branch-assign-form">
          <div class="form-group">
            <label class="form-label">Mã định danh UserProfile (UUID) <span class="required">*</span></label>
            <input type="text" [(ngModel)]="assignUserId" name="assignUserId" class="form-control" placeholder="Nhập UUID của UserProfile..." required />
          </div>

          <div class="form-group">
            <label class="form-label">Chọn chi nhánh hoạt động <span class="required">*</span></label>
            <select [(ngModel)]="assignBranchId" name="assignBranchId" class="form-select" required>
              <option value="" disabled selected>-- Chọn chi nhánh ngân hàng --</option>
              <option *ngFor="let b of branches" [value]="b.id">
                {{ b.branchName }} ({{ b.branchCode }}) - {{ b.address }}
              </option>
            </select>
          </div>

          <button type="submit" class="btn btn-primary" [disabled]="!assignUserId || !assignBranchId || assigning">
            {{ assigning ? 'Đang cập nhật...' : 'Xác nhận phân bổ chi nhánh' }}
          </button>
        </form>
      </div>

      <!-- Tab 3: Security Logs -->
      <div *ngIf="activeTab === 'audit'" class="bank-card tab-content">
        <div class="bank-card-header">
          <h3 class="bank-card-title">Nhật ký bảo mật & Sự kiện truy cập hệ thống</h3>
        </div>

        <div class="bank-table-responsive">
          <table class="bank-table">
            <thead>
              <tr>
                <th>Sự kiện</th>
                <th>Người dùng</th>
                <th>Phương thức</th>
                <th>URI</th>
                <th>Mã</th>
                <th>Mô tả</th>
                <th>Thời gian</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let log of auditLogs">
                <td><span class="badge badge-pending">{{ log.eventType }}</span></td>
                <td>{{ log.username || 'Hệ thống' }}</td>
                <td>{{ log.httpMethod }}</td>
                <td class="font-mono">{{ log.requestUri }}</td>
                <td><span class="badge" [ngClass]="log.statusCode < 400 ? 'badge-active' : 'badge-locked'">{{ log.statusCode }}</span></td>
                <td>{{ log.description }}</td>
                <td class="text-muted">{{ log.timestamp | date:'dd/MM/yyyy HH:mm:ss' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Lock/Unlock Modal -->
      <div *ngIf="targetAccount" class="modal-overlay" (click)="closeStatusModal()">
        <div class="modal-dialog" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3 class="modal-title">{{ modalAction === 'LOCK' ? 'Khóa tài khoản ngân hàng' : 'Mở khóa tài khoản' }}</h3>
            <button class="modal-close" (click)="closeStatusModal()">✕</button>
          </div>

          <div class="modal-body">
            <p>
              Tài khoản mục tiêu: <strong class="font-mono">{{ targetAccount.maskedAccountNumber | maskAccount }}</strong> ({{ targetAccount.ownerUsername }})
            </p>
            <div class="form-group mt-3">
              <label class="form-label">Lý do hành động quản trị (Bắt buộc) <span class="required">*</span></label>
              <textarea [(ngModel)]="actionReason" rows="3" class="form-control" placeholder="Nhập lý do thực hiện nghiệp vụ khóa/mở khóa này..." required></textarea>
            </div>
          </div>

          <div class="modal-footer">
            <button class="btn" [ngClass]="modalAction === 'LOCK' ? 'btn-danger' : 'btn-success'" [disabled]="!actionReason || processing" (click)="submitStatusChange()">
              {{ processing ? 'Đang thực hiện...' : (modalAction === 'LOCK' ? 'Khóa tài khoản' : 'Mở khóa tài khoản') }}
            </button>
            <button class="btn btn-secondary" (click)="closeStatusModal()">Hủy</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .admin-container { max-width: 1300px; margin: 0 auto; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
    .tabs-header { display: flex; gap: 10px; margin-bottom: 20px; border-bottom: 2px solid #e5e7eb; }
    .tab-btn { background: none; border: none; padding: 12px 20px; font-weight: 600; color: #6b7280; font-size: 0.95rem; cursor: pointer; border-bottom: 3px solid transparent; margin-bottom: -2px; }
    .tab-btn.active { color: #e53e3e; border-bottom-color: #e53e3e; }
    .branch-assign-form { max-width: 600px; }
    .required { color: #e02424; }
    .pagination-bar { display: flex; justify-content: space-between; align-items: center; padding-top: 14px; border-top: 1px solid #edf2f7; margin-top: 14px; }
    .pagination-buttons { display: flex; gap: 8px; }
    .font-mono { font-family: monospace; }
  `]
})
export class AdminDashboardComponent implements OnInit {
  private adminService = inject(AdminService);
  private branchService = inject(BranchService);
  private auditService = inject(AuditService);

  activeTab: 'accounts' | 'branches' | 'audit' = 'accounts';
  statusFilter = '';

  accounts: AdminAccountResponse[] = [];
  pageData: Page<AdminAccountResponse> | null = null;
  currentPage = 0;
  accLoading = true;

  branches: Branch[] = [];
  assignUserId = '';
  assignBranchId = '';
  assigning = false;

  auditLogs: AuditLog[] = [];

  targetAccount: AdminAccountResponse | null = null;
  modalAction: 'LOCK' | 'UNLOCK' = 'LOCK';
  actionReason = '';
  processing = false;
  actionSuccess: string | null = null;
  actionError: string | null = null;

  ngOnInit(): void {
    this.loadAccounts(0);
    this.loadBranches();
    this.loadAuditLogs();
  }

  loadAccounts(page: number): void {
    this.accLoading = true;
    this.currentPage = page;
    this.adminService.searchAccounts(this.statusFilter || undefined, undefined, page, 15).subscribe({
      next: (res) => {
        this.pageData = res;
        this.accounts = res.content;
        this.accLoading = false;
      },
      error: () => {
        this.accLoading = false;
      }
    });
  }

  loadBranches(): void {
    this.branchService.getActiveBranches().subscribe({
      next: (branches) => {
        this.branches = branches;
      }
    });
  }

  loadAuditLogs(): void {
    this.auditService.getAuditLogs().subscribe({
      next: (logs) => {
        this.auditLogs = logs;
      }
    });
  }

  openStatusModal(account: AdminAccountResponse, action: 'LOCK' | 'UNLOCK'): void {
    this.targetAccount = account;
    this.modalAction = action;
    this.actionReason = '';
    this.actionSuccess = null;
    this.actionError = null;
  }

  closeStatusModal(): void {
    this.targetAccount = null;
  }

  submitStatusChange(): void {
    if (!this.targetAccount || !this.actionReason) return;
    this.processing = true;

    const op = this.modalAction === 'LOCK'
      ? this.adminService.lockAccount(this.targetAccount.id, this.actionReason)
      : this.adminService.unlockAccount(this.targetAccount.id, this.actionReason);

    op.subscribe({
      next: (updated) => {
        this.processing = false;
        this.actionSuccess = `Đã cập nhật trạng thái tài khoản ${updated.maskedAccountNumber} thành ${updated.accountStatus}.`;
        this.closeStatusModal();
        this.loadAccounts(this.currentPage);
      },
      error: (err) => {
        this.processing = false;
        this.actionError = err?.error?.message || 'Có lỗi xảy ra khi cập nhật trạng thái tài khoản.';
      }
    });
  }

  executeBranchAssignment(): void {
    if (!this.assignUserId || !this.assignBranchId) return;
    this.assigning = true;
    this.actionSuccess = null;
    this.actionError = null;

    this.adminService.assignBranch(this.assignUserId.trim(), this.assignBranchId).subscribe({
      next: (b) => {
        this.assigning = false;
        this.actionSuccess = `Gán chi nhánh thành công: Người dùng được phân bổ vào chi nhánh ${b.branchName} (${b.branchCode}).`;
        this.assignUserId = '';
        this.assignBranchId = '';
      },
      error: (err) => {
        this.assigning = false;
        this.actionError = err?.error?.message || 'Không thể phân bổ chi nhánh cho người dùng này.';
      }
    });
  }
}

