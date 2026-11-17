import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface AuditLogItem {
  id: number;
  entityName: string;
  entityId: string;
  action: string;
  changedBy: string;
  oldValue?: string;
  newValue?: string;
  ipAddress?: string;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

@Injectable({
  providedIn: 'root'
})
export class AdminAuditService {
  private apiUrl = `${environment.apiUrl}/admin/audit-logs`;

  constructor(private http: HttpClient) {}

  getAuditLogs(entityName?: string, action?: string, page: number = 0, size: number = 20): Observable<PageResponse<AuditLogItem>> {
    let url = `${this.apiUrl}?page=${page}&size=${size}`;
    if (entityName && entityName !== 'ALL') {
      url += `&entityName=${encodeURIComponent(entityName)}`;
    }
    if (action && action !== 'ALL') {
      url += `&action=${encodeURIComponent(action)}`;
    }

    return this.http.get<{ success: boolean; data: PageResponse<AuditLogItem> }>(url)
      .pipe(map(res => res.data));
  }
}
