import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AdminAccountResponse, AccountStatusChangeRequest } from '../models/account.model';
import { Branch } from '../models/user.model';
import { Page } from '../models/transfer.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  searchAccounts(status?: string, type?: string, page = 0, size = 20): Observable<Page<AdminAccountResponse>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (status) params = params.set('status', status);
    if (type) params = params.set('type', type);

    return this.http.get<Page<AdminAccountResponse>>(`${this.baseUrl}/api/admin/accounts`, { params });
  }

  lockAccount(accountId: string, reason: string): Observable<AdminAccountResponse> {
    const body: AccountStatusChangeRequest = { reason };
    return this.http.post<AdminAccountResponse>(`${this.baseUrl}/api/admin/accounts/${accountId}/lock`, body);
  }

  unlockAccount(accountId: string, reason: string): Observable<AdminAccountResponse> {
    const body: AccountStatusChangeRequest = { reason };
    return this.http.post<AdminAccountResponse>(`${this.baseUrl}/api/admin/accounts/${accountId}/unlock`, body);
  }

  assignBranch(userProfileId: string, branchId: string): Observable<Branch> {
    return this.http.patch<Branch>(`${this.baseUrl}/api/admin/users/${userProfileId}/branch`, { branchId });
  }
}

