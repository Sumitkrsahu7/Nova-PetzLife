import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ItemEligibility {
  orderItemId: number;
  productId: number;
  productName: string;
  brandName: string;
  sku: string;
  quantity: number;
  unitPrice: number;
  isReturnAllowed: boolean;
  returnWindowDays: number;
  returnDeadline: string;
  isWithinWindow: boolean;
  isEligible: boolean;
  reasonIfNotEligible?: string;
  originalPackagingRequired: boolean;
  unusedConditionRequired: boolean;
  conditions?: string;
}

export interface ReturnEligibility {
  orderNumber: string;
  orderStatus: string;
  orderDate: string;
  canRequestReturn: boolean;
  generalEligibilityMessage: string;
  items: ItemEligibility[];
}

export interface ReturnItemRequest {
  orderItemId: number;
  quantity: number;
}

export interface CreateReturnPayload {
  orderNumber: string;
  items: ReturnItemRequest[];
  reason: string;
  customerComments?: string;
}

export interface ReturnItemDetail {
  id: number;
  orderItemId: number;
  productName: string;
  brandName: string;
  sku: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
}

export interface InspectionDetail {
  id: number;
  inspectedBy: string;
  outcome: string;
  conditionNotes: string;
  inspectedAt: string;
}

export interface ReturnRequestDetail {
  id: number;
  requestNumber: string;
  orderNumber: string;
  customerName: string;
  customerPhone?: string;
  customerEmail?: string;
  status: string;
  reason: string;
  customerComments?: string;
  adminNotes?: string;
  estimatedRefundAmount: number;
  items: ReturnItemDetail[];
  inspections: InspectionDetail[];
  createdAt: string;
  updatedAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class ReturnService {
  private apiUrl = `${environment.apiUrl}`;

  constructor(private http: HttpClient) {}

  public getReturnEligibility(orderNumber: string): Observable<{ success: boolean; data: ReturnEligibility }> {
    return this.http.get<{ success: boolean; data: ReturnEligibility }>(`${this.apiUrl}/orders/${orderNumber}/return-eligibility`);
  }

  public submitReturnRequest(payload: CreateReturnPayload): Observable<{ success: boolean; data: ReturnRequestDetail }> {
    return this.http.post<{ success: boolean; data: ReturnRequestDetail }>(`${this.apiUrl}/returns`, payload);
  }

  public getReturnRequest(requestNumber: string): Observable<{ success: boolean; data: ReturnRequestDetail }> {
    return this.http.get<{ success: boolean; data: ReturnRequestDetail }>(`${this.apiUrl}/returns/${requestNumber}`);
  }

  public cancelOrder(orderNumber: string, reason: string): Observable<{ success: boolean; data: any }> {
    return this.http.post<{ success: boolean; data: any }>(`${this.apiUrl}/orders/${orderNumber}/cancel`, { reason });
  }
}
