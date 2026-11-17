import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface DeliveryEligibility {
  isEligible: boolean;
  distanceKm: number;
  deliveryRadiusKm: number;
  deliveryCharge: number;
  freeDeliveryThreshold: number;
  minOrderValue: number;
  isShopVerified: boolean;
  shopName: string;
  shopAddress: string;
  message: string;
}

export interface DeliverySlot {
  id: number;
  slotName: string;
  startTime: string;
  endTime: string;
  maxCapacity: number;
  isActive: boolean;
}

export interface ShopInfo {
  shopName: string;
  addressLine: string;
  city: string;
  state: string;
  pinCode: string;
  latitude: number;
  longitude: number;
  deliveryRadiusKm: number;
  isVerified: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class DeliveryService {
  private apiUrl = `${environment.apiUrl}/delivery`;

  constructor(private http: HttpClient) { }

  public checkEligibility(latitude: number, longitude: number, orderSubtotal: number = 0, postalCode?: string): Observable<{ success: boolean; data: DeliveryEligibility }> {
    return this.http.post<{ success: boolean; data: DeliveryEligibility }>(`${this.apiUrl}/check-eligibility`, {
      latitude,
      longitude,
      orderSubtotal,
      postalCode
    });
  }

  public getSlots(): Observable<{ success: boolean; data: DeliverySlot[] }> {
    return this.http.get<{ success: boolean; data: DeliverySlot[] }>(`${this.apiUrl}/slots`);
  }

  public getShopInfo(): Observable<{ success: boolean; data: ShopInfo }> {
    return this.http.get<{ success: boolean; data: ShopInfo }>(`${this.apiUrl}/shop-info`);
  }
}
