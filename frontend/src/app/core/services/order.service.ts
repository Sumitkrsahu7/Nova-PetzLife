import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface CheckoutPayload {
  sessionToken?: string;
  guestEmail?: string;
  guestPhone?: string;
  guestFirstName?: string;
  guestLastName?: string;
  address?: {
    recipientName: string;
    phone: string;
    addressLine1: string;
    addressLine2?: string;
    landmark?: string;
    city: string;
    state: string;
    postalCode: string;
    latitude: number;
    longitude: number;
    isDefault?: boolean;
  };
  latitude: number;
  longitude: number;
  deliverySlot?: string;
  paymentMethod: string;
  couponCode?: string;
  notes?: string;
}

export interface OrderItem {
  id: number;
  productId: number;
  productName: string;
  brandName: string;
  sku: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  returnPolicySnapshot: string;
  refundPolicySnapshot: string;
}

export interface OrderStatusHistory {
  id: number;
  previousStatus?: string;
  newStatus: string;
  changedBy: string;
  reason?: string;
  createdAt: string;
}

export interface OrderDetail {
  id: number;
  orderNumber: string;
  customerName: string;
  customerEmail?: string;
  customerPhone?: string;
  orderStatus: string;
  paymentStatus: string;
  paymentMethod: string;
  totalAmount: number;
  discountAmount: number;
  deliveryCharge: number;
  taxAmount: number;
  finalPayableAmount: number;
  deliveryAddressJson: string;
  shopDistanceKm: number;
  isWithinRadius: boolean;
  deliverySlot?: string;
  items: OrderItem[];
  statusHistory: OrderStatusHistory[];
  createdAt: string;
}

export interface OrderTracking {
  orderNumber: string;
  orderStatus: string;
  paymentStatus: string;
  paymentMethod: string;
  deliverySlot?: string;
  shopDistanceKm: number;
  deliveryAddressJson: string;
  orderDate: string;
  timeline: OrderStatusHistory[];
}

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private apiUrl = `${environment.apiUrl}/orders`;

  constructor(private http: HttpClient) {}

  public checkout(payload: CheckoutPayload): Observable<{ success: boolean; data: OrderDetail }> {
    return this.http.post<{ success: boolean; data: OrderDetail }>(`${this.apiUrl}/checkout`, payload);
  }

  public trackOrder(orderNumber: string): Observable<{ success: boolean; data: OrderTracking }> {
    return this.http.get<{ success: boolean; data: OrderTracking }>(`${this.apiUrl}/${orderNumber}/track`);
  }
}
