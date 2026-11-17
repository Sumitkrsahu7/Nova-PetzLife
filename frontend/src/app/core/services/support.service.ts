import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface StoreInfo {
  shopName: string;
  listingName: string;
  address: string;
  landmark: string;
  city: string;
  state: string;
  country: string;
  postalCode: string;
  latitude: number;
  longitude: number;
  deliveryRadiusKm: number;
  phone: string;
  whatsapp: string;
  email: string;
  openingHours: string;
  availableServices: string[];
}

export interface FaqItem {
  category: string;
  question: string;
  answer: string;
}

export interface SupportMessage {
  id: number;
  senderType: string;
  senderId: string;
  message: string;
  attachmentUrl?: string;
  createdAt: string;
}

export interface SupportTicket {
  id: number;
  ticketNumber: string;
  customerId: number;
  customerName: string;
  customerEmail?: string;
  customerPhone?: string;
  orderNumber?: string;
  subject: string;
  priority: string;
  status: string;
  assignedTo?: string;
  messages: SupportMessage[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateInquiryPayload {
  name: string;
  email: string;
  phone: string;
  orderNumber?: string;
  subject: string;
  message: string;
  priority?: string;
}

@Injectable({
  providedIn: 'root'
})
export class SupportService {
  private apiUrl = `${environment.apiUrl}/support`;

  constructor(private http: HttpClient) {}

  public getStoreInfo(): Observable<{ success: boolean; data: StoreInfo }> {
    return this.http.get<{ success: boolean; data: StoreInfo }>(`${this.apiUrl}/store-info`);
  }

  public getFaqs(): Observable<{ success: boolean; data: FaqItem[] }> {
    return this.http.get<{ success: boolean; data: FaqItem[] }>(`${this.apiUrl}/faqs`);
  }

  public submitInquiry(payload: CreateInquiryPayload): Observable<{ success: boolean; data: SupportTicket }> {
    return this.http.post<{ success: boolean; data: SupportTicket }>(`${this.apiUrl}/inquiries`, payload);
  }

  public getTicket(ticketNumber: string): Observable<{ success: boolean; data: SupportTicket }> {
    return this.http.get<{ success: boolean; data: SupportTicket }>(`${this.apiUrl}/tickets/${ticketNumber}`);
  }

  public replyTicket(ticketNumber: string, message: string, senderId?: string): Observable<{ success: boolean; data: SupportTicket }> {
    return this.http.post<{ success: boolean; data: SupportTicket }>(`${this.apiUrl}/tickets/${ticketNumber}/messages`, {
      message,
      senderType: 'CUSTOMER',
      senderId: senderId || 'Customer'
    });
  }
}
