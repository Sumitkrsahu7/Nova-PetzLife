import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AnalyticsSummary {
  totalRevenue: number;
  totalOrders: number;
  deliveredOrders: number;
  cancelledOrders: number;
  averageOrderValue: number;
  totalRefunded: number;
  activeCouponsCount: number;
  lowStockCount: number;
  ordersWithin20kmCount: number;
  ordersByStatus: { [key: string]: number };
  paymentMethodBreakdown: { [key: string]: number };
}

export interface LowStockAlert {
  productId: number;
  productName: string;
  sku: string;
  categoryName: string;
  currentStock: number;
  lowStockThreshold: number;
  suggestedReorder: number;
}

export interface TopProduct {
  productId: number;
  productName: string;
  brandName: string;
  totalQuantitySold: number;
  totalRevenue: number;
}

@Injectable({
  providedIn: 'root'
})
export class AdminAnalyticsService {
  private apiUrl = `${environment.apiUrl}/admin/analytics`;

  constructor(private http: HttpClient) {}

  private getAuthHeaders(): HttpHeaders {
    return new HttpHeaders();
  }

  public getSummary(): Observable<{ success: boolean; data: AnalyticsSummary }> {
    return this.http.get<{ success: boolean; data: AnalyticsSummary }>(`${this.apiUrl}/summary`, {
      headers: this.getAuthHeaders()
    });
  }

  public getLowStockAlerts(): Observable<{ success: boolean; data: LowStockAlert[] }> {
    return this.http.get<{ success: boolean; data: LowStockAlert[] }>(`${this.apiUrl}/low-stock`, {
      headers: this.getAuthHeaders()
    });
  }

  public getTopProducts(limit: number = 10): Observable<{ success: boolean; data: TopProduct[] }> {
    return this.http.get<{ success: boolean; data: TopProduct[] }>(`${this.apiUrl}/top-products?limit=${limit}`, {
      headers: this.getAuthHeaders()
    });
  }
}
