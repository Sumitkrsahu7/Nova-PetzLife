import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface StaffPermission {
  id: number;
  name: string;
  description: string;
}

export interface StaffMember {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  phone: string;
  isActive: boolean;
  roles: string[];
  permissions: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateStaffPayload {
  email: string;
  password?: string;
  firstName: string;
  lastName?: string;
  phone?: string;
  permissions: string[];
}

@Injectable({
  providedIn: 'root'
})
export class AdminStaffService {
  private apiUrl = `${environment.apiUrl}/admin/staff`;

  constructor(private http: HttpClient) {}

  private getAuthHeaders(): HttpHeaders {
    return new HttpHeaders();
  }

  getStaffMembers(): Observable<{ success: boolean; data: StaffMember[] }> {
    return this.http.get<{ success: boolean; data: StaffMember[] }>(this.apiUrl, {
      headers: this.getAuthHeaders()
    });
  }

  getAvailablePermissions(): Observable<{ success: boolean; data: StaffPermission[] }> {
    return this.http.get<{ success: boolean; data: StaffPermission[] }>(`${this.apiUrl}/permissions`, {
      headers: this.getAuthHeaders()
    });
  }

  createStaff(payload: CreateStaffPayload): Observable<{ success: boolean; data: StaffMember }> {
    return this.http.post<{ success: boolean; data: StaffMember }>(this.apiUrl, payload, {
      headers: this.getAuthHeaders()
    });
  }

  updateStaff(id: number, payload: Partial<CreateStaffPayload>): Observable<{ success: boolean; data: StaffMember }> {
    return this.http.put<{ success: boolean; data: StaffMember }>(`${this.apiUrl}/${id}`, payload, {
      headers: this.getAuthHeaders()
    });
  }

  updatePermissions(id: number, permissions: string[]): Observable<{ success: boolean; data: StaffMember }> {
    return this.http.put<{ success: boolean; data: StaffMember }>(`${this.apiUrl}/${id}/permissions`, { permissions }, {
      headers: this.getAuthHeaders()
    });
  }

  resetPassword(id: number, newPassword: string): Observable<{ success: boolean; message: string }> {
    return this.http.post<{ success: boolean; message: string }>(`${this.apiUrl}/${id}/reset-password`, { newPassword }, {
      headers: this.getAuthHeaders()
    });
  }

  toggleStaffStatus(id: number): Observable<{ success: boolean; data: StaffMember }> {
    return this.http.patch<{ success: boolean; data: StaffMember }>(`${this.apiUrl}/${id}/toggle-status`, {}, {
      headers: this.getAuthHeaders()
    });
  }
}
