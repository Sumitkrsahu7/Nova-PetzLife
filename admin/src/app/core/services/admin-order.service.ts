import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AdminOrderSummary {
  id: number;
  orderNumber: string;
  customerName: string;
  customerPhone: string;
  orderStatus: string;
  paymentStatus: string;
  paymentMethod: string;
  finalPayableAmount: number;
  totalItems: number;
  deliverySlot?: string;
  shopDistanceKm: number;
  createdAt: string;
}

export interface AdminOrderItem {
  id: number;
  productName: string;
  brandName: string;
  sku: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  returnPolicySnapshot: string;
  refundPolicySnapshot: string;
}

export interface AdminOrderDetail {
  id: number;
  orderNumber: string;
  customerName: string;
  customerEmail?: string;
  customerPhone?: string;
  orderStatus: string;
  paymentStatus: string;
  paymentMethod: string;
  totalAmount: number;
  deliveryCharge: number;
  finalPayableAmount: number;
  deliveryAddressJson: string;
  shopDistanceKm: number;
  deliverySlot?: string;
  items: AdminOrderItem[];
  statusHistory: any[];
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class AdminOrderService {
  private apiUrl = `${environment.apiUrl}/admin`;

  constructor(private http: HttpClient) {}

  private getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('nova_admin_token') || 'demo_super_admin_jwt';
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
  }

  public getOrders(status?: string, query?: string, page: number = 0, size: number = 20): Observable<{ success: boolean; data: { content: AdminOrderSummary[]; totalElements: number } }> {
    let url = `${this.apiUrl}/orders?page=${page}&size=${size}`;
    if (status && status !== 'ALL') url += `&status=${status}`;
    if (query) url += `&query=${encodeURIComponent(query)}`;

    return this.http.get<{ success: boolean; data: { content: AdminOrderSummary[]; totalElements: number } }>(url, { headers: this.getAuthHeaders() });
  }

  public getOrderDetail(orderNumber: string): Observable<{ success: boolean; data: AdminOrderDetail }> {
    return this.http.get<{ success: boolean; data: AdminOrderDetail }>(`${this.apiUrl}/orders/${orderNumber}`, { headers: this.getAuthHeaders() });
  }

  public updateOrderStatus(orderId: number, newStatus: string, reason?: string, deliveryOtp?: string): Observable<{ success: boolean; data: AdminOrderDetail }> {
    return this.http.patch<{ success: boolean; data: AdminOrderDetail }>(
      `${this.apiUrl}/orders/${orderId}/status`,
      { newStatus, reason, deliveryOtp },
      { headers: this.getAuthHeaders() }
    );
  }

  public verifyDeliveryOtp(orderId: number, otp: string): Observable<{ success: boolean; data: AdminOrderDetail }> {
    return this.http.post<{ success: boolean; data: AdminOrderDetail }>(
      `${this.apiUrl}/orders/${orderId}/verify-delivery-otp`,
      { otp },
      { headers: this.getAuthHeaders() }
    );
  }

  public verifyShopLocation(isVerified: boolean): Observable<{ success: boolean; data: any }> {
    return this.http.patch<{ success: boolean; data: any }>(
      `${this.apiUrl}/delivery/shop-location/verify`,
      { isVerified },
      { headers: this.getAuthHeaders() }
    );
  }
}
