import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AdminSupportMessage {
  id: number;
  senderType: string;
  senderId: string;
  message: string;
  attachmentUrl?: string;
  createdAt: string;
}

export interface AdminSupportTicket {
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
  messages: AdminSupportMessage[];
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

@Injectable({
  providedIn: 'root'
})
export class AdminSupportService {
  private apiUrl = `${environment.apiUrl}/admin/support/tickets`;

  constructor(private http: HttpClient) {}

  private getAuthHeaders(): HttpHeaders {
    return new HttpHeaders();
  }

  public getTickets(status: string = 'ALL', page: number = 0, size: number = 20): Observable<{ success: boolean; data: PageResponse<AdminSupportTicket> }> {
    let params = new HttpParams()
      .set('status', status)
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<{ success: boolean; data: PageResponse<AdminSupportTicket> }>(this.apiUrl, {
      headers: this.getAuthHeaders(),
      params
    });
  }

  public getTicketDetail(ticketNumber: string): Observable<{ success: boolean; data: AdminSupportTicket }> {
    return this.http.get<{ success: boolean; data: AdminSupportTicket }>(`${this.apiUrl}/${ticketNumber}`, {
      headers: this.getAuthHeaders()
    });
  }

  public staffReply(ticketNumber: string, message: string, staffName: string = 'Staff Admin'): Observable<{ success: boolean; data: AdminSupportTicket }> {
    return this.http.post<{ success: boolean; data: AdminSupportTicket }>(`${this.apiUrl}/${ticketNumber}/reply`, {
      message,
      senderType: 'STAFF',
      senderId: staffName
    }, {
      headers: this.getAuthHeaders()
    });
  }

  public updateStatus(id: number, status: string, assignedTo?: string): Observable<{ success: boolean; data: AdminSupportTicket }> {
    let params = new HttpParams().set('status', status);
    if (assignedTo) {
      params = params.set('assignedTo', assignedTo);
    }
    return this.http.patch<{ success: boolean; data: AdminSupportTicket }>(`${this.apiUrl}/${id}/status`, {}, {
      headers: this.getAuthHeaders(),
      params
    });
  }
}
