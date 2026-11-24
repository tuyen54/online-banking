import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TransferRequest, TransferResponse, TransactionSummary, Page } from '../models/transfer.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class TransferService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  generateIdempotencyKey(): string {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      return crypto.randomUUID();
    }
    return 'key-' + Date.now() + '-' + Math.random().toString(36).substring(2, 15);
  }

  executeTransfer(request: TransferRequest, idempotencyKey: string): Observable<TransferResponse> {
    const headers = new HttpHeaders({
      'Idempotency-Key': idempotencyKey,
      'Content-Type': 'application/json'
    });
    return this.http.post<TransferResponse>(`${this.baseUrl}/api/transfers`, request, { headers });
  }

  getTransactions(page = 0, size = 20): Observable<Page<TransactionSummary>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', 'createdAt,desc');
    return this.http.get<Page<TransactionSummary>>(`${this.baseUrl}/api/transactions`, { params });
  }

  getTransactionDetails(transactionId: string): Observable<TransactionSummary> {
    return this.http.get<TransactionSummary>(`${this.baseUrl}/api/transactions/${transactionId}`);
  }
}

