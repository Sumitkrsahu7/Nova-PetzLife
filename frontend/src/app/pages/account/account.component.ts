import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService, UserProfile } from '../../core/services/auth.service';
import { environment } from '../../../environments/environment';

export interface Address {
  id?: number;
  recipientName: string;
  phone: string;
  addressLine1: string;
  addressLine2?: string;
  landmark?: string;
  city: string;
  state: string;
  postalCode: string;
  latitude?: number;
  longitude?: number;
  isDefault?: boolean;
}

export interface CustomerOrderSummary {
  id: number;
  orderNumber: string;
  orderStatus: string;
  paymentStatus: string;
  paymentMethod: string;
  totalAmount: number;
  finalPayableAmount: number;
  deliverySlot: string;
  itemCount: number;
  createdAt: string;
}

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './account.component.html',
  styleUrls: ['./account.component.scss']
})
export class AccountComponent implements OnInit {
  user: UserProfile | null = null;
  activeTab: 'profile' | 'addresses' | 'orders' = 'profile';

  // Addresses
  addresses: Address[] = [];
  isLoadingAddresses = false;
  showAddAddress = false;
  addressForm: Address = {
    recipientName: '',
    phone: '',
    addressLine1: '',
    addressLine2: '',
    landmark: '',
    city: 'Thiruvananthapuram',
    state: 'Kerala',
    postalCode: '695003',
    latitude: 8.5244,
    longitude: 76.9442,
    isDefault: true
  };
  addressSaveMessage = '';

  // Popular Trivandrum neighbourhoods
  trivandrumLocations = [
    { name: 'Pattom Junction (1.1 km)', lat: 8.5244, lon: 76.9442, pin: '695004' },
    { name: 'Kowdiar Palace (0.9 km)', lat: 8.5241, lon: 76.9602, pin: '695003' },
    { name: 'Ambalamukku (0.7 km)', lat: 8.5320, lon: 76.9550, pin: '695005' },
    { name: 'Palayam / Statue (3.2 km)', lat: 8.5020, lon: 76.9500, pin: '695001' },
    { name: 'Sasthamangalam (2.8 km)', lat: 8.5140, lon: 76.9740, pin: '695010' },
    { name: 'Technopark / Kazhakkoottam (8.6 km)', lat: 8.5583, lon: 76.8812, pin: '695581' }
  ];

  // Orders
  orders: CustomerOrderSummary[] = [];
  isLoadingOrders = false;

  constructor(
    private authService: AuthService,
    private http: HttpClient,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: '/account' } });
      return;
    }

    this.user = this.authService.getCurrentUser();
    this.loadAddresses();
    this.loadOrders();
  }

  setTab(tab: 'profile' | 'addresses' | 'orders'): void {
    this.activeTab = tab;
  }

  loadAddresses(): void {
    this.isLoadingAddresses = true;
    this.http.get<any>(`${environment.apiUrl}/customer/addresses`).subscribe({
      next: res => {
        this.isLoadingAddresses = false;
        if (res?.success && res.data) {
          this.addresses = res.data;
        }
      },
      error: () => {
        this.isLoadingAddresses = false;
      }
    });
  }

  onPresetLocationChange(event: any): void {
    const selected = this.trivandrumLocations.find(loc => loc.name === event.target.value);
    if (selected) {
      this.addressForm.latitude = selected.lat;
      this.addressForm.longitude = selected.lon;
      this.addressForm.postalCode = selected.pin;
      this.addressForm.landmark = selected.name.split(' (')[0];
    }
  }

  saveAddress(): void {
    if (!this.addressForm.recipientName || !this.addressForm.phone || !this.addressForm.addressLine1) {
      return;
    }

    this.http.post<any>(`${environment.apiUrl}/customer/addresses`, this.addressForm).subscribe({
      next: res => {
        if (res?.success) {
          this.showAddAddress = false;
          this.addressSaveMessage = 'Address saved successfully!';
          this.loadAddresses();
          setTimeout(() => this.addressSaveMessage = '', 3000);
        }
      }
    });
  }

  deleteAddress(id?: number): void {
    if (!id) return;
    if (confirm('Are you sure you want to delete this address?')) {
      this.http.delete<any>(`${environment.apiUrl}/customer/addresses/${id}`).subscribe({
        next: () => {
          this.loadAddresses();
        }
      });
    }
  }

  loadOrders(): void {
    this.isLoadingOrders = true;
    this.http.get<any>(`${environment.apiUrl}/customer/orders?page=0&size=20`).subscribe({
      next: res => {
        this.isLoadingOrders = false;
        if (res?.success && res.data?.content) {
          this.orders = res.data.content;
        }
      },
      error: () => {
        this.isLoadingOrders = false;
      }
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/']);
  }
}
