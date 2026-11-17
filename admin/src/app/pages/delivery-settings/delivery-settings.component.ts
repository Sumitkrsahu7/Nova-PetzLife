import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface ShopLocationData {
  id?: number;
  shopName: string;
  addressLine: string;
  city: string;
  state: string;
  country: string;
  pinCode: string;
  latitude: number;
  longitude: number;
  deliveryRadiusKm: number;
  isVerified: boolean;
}

export interface DeliverySlotData {
  id: number;
  slotName: string;
  startTime: string;
  endTime: string;
  cutoffMinutes: number;
  maxOrders: number;
  isActive: boolean;
}

@Component({
  selector: 'app-delivery-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './delivery-settings.component.html',
  styleUrls: ['./delivery-settings.component.scss']
})
export class DeliverySettingsComponent implements OnInit {
  shopLocation: ShopLocationData = {
    shopName: 'NOVA PETZLIFE',
    addressLine: 'Petzlife, Kuravankonam Junction, Kuravankonam Ambalamukku Road',
    city: 'Thiruvananthapuram',
    state: 'Kerala',
    country: 'India',
    pinCode: '695003',
    latitude: 8.5284480,
    longitude: 76.9532680,
    deliveryRadiusKm: 20.0,
    isVerified: false
  };

  deliverySettings = {
    deliveryCharge: 50.00,
    freeDeliveryThreshold: 500.00,
    minOrderValue: 100.00,
    prepTimeMinutes: 60,
    isDeliveryEnabled: true
  };

  slots: DeliverySlotData[] = [];
  isLoading = false;
  successMessage = '';
  errorMessage = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchData();
  }

  fetchData(): void {
    this.isLoading = true;
    this.http.get<any>(`${environment.apiUrl}/delivery/shop-info`).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data) {
          const d = res.data;
          this.shopLocation.shopName = d.shopName || this.shopLocation.shopName;
          this.shopLocation.addressLine = d.addressLine || this.shopLocation.addressLine;
          this.shopLocation.latitude = d.latitude || 8.5284480;
          this.shopLocation.longitude = d.longitude || 76.9532680;
          this.shopLocation.deliveryRadiusKm = d.deliveryRadiusKm || 20.0;
          this.shopLocation.isVerified = !!d.isVerified;

          if (d.settings) {
            this.deliverySettings.deliveryCharge = d.settings.deliveryCharge;
            this.deliverySettings.freeDeliveryThreshold = d.settings.freeDeliveryThreshold;
            this.deliverySettings.minOrderValue = d.settings.minOrderValue;
          }
        }
      },
      error: () => {
        this.isLoading = false;
      }
    });

    this.http.get<any>(`${environment.apiUrl}/delivery/slots`).subscribe(res => {
      if (res?.success && res.data) {
        this.slots = res.data;
      }
    });
  }

  toggleOwnerVerification(): void {
    const nextVerified = !this.shopLocation.isVerified;
    this.http.patch<any>(`${environment.apiUrl}/admin/delivery/shop-location/verify`, { isVerified: nextVerified }).subscribe({
      next: res => {
        if (res?.success) {
          this.shopLocation.isVerified = nextVerified;
          this.successMessage = nextVerified
            ? 'Store location verified! Delivery radius of 20 km is now fully ACTIVE.'
            : 'Store location unverified. Delivery eligibility suspended.';
          setTimeout(() => this.successMessage = '', 4000);
        }
      },
      error: err => {
        this.errorMessage = err.error?.message || 'Failed to update store verification status';
        setTimeout(() => this.errorMessage = '', 4000);
      }
    });
  }
}
