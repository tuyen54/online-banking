import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuditLog } from '../models/audit.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuditService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  getAuditLogs(eventType?: string, from?: string, to?: string): Observable<AuditLog[]> {
    let params = new HttpParams();
    if (eventType) params = params.set('eventType', eventType);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);

    return this.http.get<AuditLog[]>(`${this.baseUrl}/api/audit-logs`, { params });
  }
}

