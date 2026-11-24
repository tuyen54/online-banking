import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Branch } from '../models/user.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class BranchService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  getActiveBranches(): Observable<Branch[]> {
    return this.http.get<Branch[]>(`${this.baseUrl}/api/branches`);
  }
}

