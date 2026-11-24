import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BankAccount, AccountCreationRequest } from '../models/account.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AccountService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  getMyAccounts(): Observable<BankAccount[]> {
    return this.http.get<BankAccount[]>(`${this.baseUrl}/api/accounts`);
  }

  getAccountDetails(accountId: string): Observable<BankAccount> {
    return this.http.get<BankAccount>(`${this.baseUrl}/api/accounts/${accountId}`);
  }

  openAccount(request: AccountCreationRequest): Observable<BankAccount> {
    return this.http.post<BankAccount>(`${this.baseUrl}/api/accounts`, request);
  }
}

