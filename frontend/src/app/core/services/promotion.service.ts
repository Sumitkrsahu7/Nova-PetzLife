import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Coupon {
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
}

export interface CouponValidationResponse {
  valid: boolean;
  code: string;
  message: string;
  discountType?: 'PERCENTAGE' | 'FIXED';
  discountValue?: number;
  discountAmount: number;
  newSubtotal: number;
  originalSubtotal: number;
}

@Injectable({
  providedIn: 'root'
})
export class PromotionService {
  private apiUrl = `${environment.apiUrl}/promotions/coupons`;

  constructor(private http: HttpClient) {}

  public getActiveCoupons(): Observable<{ success: boolean; data: Coupon[] }> {
    return this.http.get<{ success: boolean; data: Coupon[] }>(`${this.apiUrl}/active`);
  }

  public validateCoupon(code: string, cartSubtotal: number, customerId?: number): Observable<{ success: boolean; data: CouponValidationResponse }> {
    return this.http.post<{ success: boolean; data: CouponValidationResponse }>(`${this.apiUrl}/validate`, {
      code,
      cartSubtotal,
      customerId
    });
  }
}
