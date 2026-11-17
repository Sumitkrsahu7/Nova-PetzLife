import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

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

export interface ReturnRequestSummary {
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

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface InspectPayload {
  outcome: string;
  conditionNotes: string;
  restockInventory: boolean;
  approveRefund: boolean;
  refundAmount?: number;
  adminNotes?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AdminReturnService {
  private apiUrl = `${environment.apiUrl}/admin/returns`;

  constructor(private http: HttpClient) {}

  public getReturnRequests(status: string = 'ALL', page: number = 0, size: number = 15): Observable<{ success: boolean; data: PageResponse<ReturnRequestSummary> }> {
    let params = new HttpParams()
      .set('status', status)
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<{ success: boolean; data: PageResponse<ReturnRequestSummary> }>(this.apiUrl, { params });
  }

  public getReturnRequest(requestNumber: string): Observable<{ success: boolean; data: ReturnRequestSummary }> {
    return this.http.get<{ success: boolean; data: ReturnRequestSummary }>(`${this.apiUrl}/${requestNumber}`);
  }

  public schedulePickup(id: number, adminNotes?: string): Observable<{ success: boolean; data: ReturnRequestSummary }> {
    return this.http.post<{ success: boolean; data: ReturnRequestSummary }>(`${this.apiUrl}/${id}/pickup`, { adminNotes });
  }

  public markReceived(id: number, adminNotes?: string): Observable<{ success: boolean; data: ReturnRequestSummary }> {
    return this.http.post<{ success: boolean; data: ReturnRequestSummary }>(`${this.apiUrl}/${id}/receive`, { adminNotes });
  }

  public inspectReturn(id: number, payload: InspectPayload): Observable<{ success: boolean; data: ReturnRequestSummary }> {
    return this.http.post<{ success: boolean; data: ReturnRequestSummary }>(`${this.apiUrl}/${id}/inspect`, payload);
  }
}
