import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AdminCoupon {
  id: number;
  code: string;
  description: string;
  discountType: 'PERCENTAGE' | 'FIXED';
  discountValue: number;
  minOrderValue: number;
  maxDiscountAmount?: number;
  validFrom: string;
  validUntil: string;
  isActive: boolean;
  totalUsageLimit?: number;
  perCustomerUsageLimit?: number;
  currentUsageCount: number;
  createdAt: string;
}

export interface CreateCouponPayload {
  code: string;
  description: string;
  discountType: 'PERCENTAGE' | 'FIXED';
  discountValue: number;
  minOrderValue: number;
  maxDiscountAmount?: number;
  validFrom: string;
  validUntil: string;
  isActive?: boolean;
  totalUsageLimit?: number;
  perCustomerUsageLimit?: number;
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
export class AdminCouponService {
  private apiUrl = `${environment.apiUrl}/admin/promotions/coupons`;

  constructor(private http: HttpClient) {}

  private getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('nova_admin_token') || 'demo_super_admin_jwt';
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
  }

  public getCoupons(page: number = 0, size: number = 20): Observable<{ success: boolean; data: PageResponse<AdminCoupon> }> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<{ success: boolean; data: PageResponse<AdminCoupon> }>(this.apiUrl, {
      headers: this.getAuthHeaders(),
      params
    });
  }

  public createCoupon(payload: CreateCouponPayload): Observable<{ success: boolean; data: AdminCoupon }> {
    return this.http.post<{ success: boolean; data: AdminCoupon }>(this.apiUrl, payload, {
      headers: this.getAuthHeaders()
    });
  }

  public toggleCoupon(id: number): Observable<{ success: boolean; data: AdminCoupon }> {
    return this.http.put<{ success: boolean; data: AdminCoupon }>(`${this.apiUrl}/${id}/toggle`, {}, {
      headers: this.getAuthHeaders()
    });
  }

  public deleteCoupon(id: number): Observable<{ success: boolean; data: null }> {
    return this.http.delete<{ success: boolean; data: null }>(`${this.apiUrl}/${id}`, {
      headers: this.getAuthHeaders()
    });
  }
}
