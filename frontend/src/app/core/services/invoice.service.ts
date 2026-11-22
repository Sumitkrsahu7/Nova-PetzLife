import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface InvoiceItem {
  itemNumber: number;
  productName: string;
  brandName: string;
  sku: string;
  hsnCode: string;
  quantity: number;
  unitPrice: number;
  taxableAmount: number;
  cgstRate: number;
  cgstAmount: number;
  sgstRate: number;
  sgstAmount: number;
  grossTotal: number;
  returnPolicySummary: string;
}

export interface InvoiceDto {
  storeName: string;
  storeAddress: string;
  storeCity: string;
  storeState: string;
  storeStateCode: string;
  storePinCode: string;
  storeGstin: string;
  storePhone: string;
  invoiceNumber: string;
  invoiceDate: string;
  orderNumber: string;
  orderDate: string;
  paymentMethod: string;
  paymentStatus: string;
  deliverySlot: string;
  placeOfSupply: string;
  reverseCharge: string;
  customerName: string;
  customerPhone: string;
  customerEmail: string;
  deliveryAddress: string;
  items: InvoiceItem[];
  totalTaxableValue: number;
  totalCgst: number;
  totalSgst: number;
  totalIgst: number;
  discountAmount?: number;
  deliveryCharge: number;
  grandTotal?: number;
  finalPayableAmount: number;
  amountInWords: string;
  documentType?: string;
}

@Injectable({
  providedIn: 'root'
})
export class InvoiceService {
  private apiUrl = `${environment.apiUrl}/orders`;

  constructor(private http: HttpClient) { }

  public getInvoice(orderNumber: string): Observable<{ success: boolean; data: InvoiceDto }> {
    return this.http.get<{ success: boolean; data: InvoiceDto }>(`${this.apiUrl}/${orderNumber}/invoice`);
  }

  public downloadInvoicePdf(orderNumber: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${orderNumber}/invoice/download`, {
      responseType: 'blob'
    });
  }
}
