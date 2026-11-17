import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface PaymentOrderResponse {
  razorpayOrderId: string;
  amountInPaise: number;
  amount: number;
  currency: string;
  keyId: string;
  orderNumber: string;
  customerName: string;
  customerEmail: string;
  customerPhone: string;
}

export interface VerifyPaymentRequest {
  orderNumber: string;
  razorpayOrderId: string;
  razorpayPaymentId: string;
  razorpaySignature: string;
}

export interface PaymentStatusResponse {
  orderNumber: string;
  paymentStatus: string;
  orderStatus: string;
  transactionId?: string;
  amount: number;
  success: boolean;
  failureReason?: string;
}

@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  private apiUrl = `${environment.apiUrl}/payments`;

  constructor(private http: HttpClient) { }

  public createPaymentOrder(orderNumber: string): Observable<{ success: boolean; data: PaymentOrderResponse }> {
    return this.http.post<{ success: boolean; data: PaymentOrderResponse }>(`${this.apiUrl}/create-order`, { orderNumber });
  }

  public verifyPayment(payload: VerifyPaymentRequest): Observable<{ success: boolean; data: PaymentStatusResponse }> {
    return this.http.post<{ success: boolean; data: PaymentStatusResponse }>(`${this.apiUrl}/verify`, payload);
  }

  public getPaymentStatus(orderNumber: string): Observable<{ success: boolean; data: PaymentStatusResponse }> {
    return this.http.get<{ success: boolean; data: PaymentStatusResponse }>(`${this.apiUrl}/order/${orderNumber}`);
  }
}
