import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService, UserProfile } from '../../core/services/auth.service';
import { CartService } from '../../core/services/cart.service';
import { PetService, PetProfile } from '../../core/services/pet.service';
import { environment } from '../../../environments/environment';
import { BackButtonComponent } from '../../components/back-button/back-button.component';

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
  imports: [CommonModule, FormsModule, RouterModule, BackButtonComponent],
  templateUrl: './account.component.html',
  styleUrls: ['./account.component.scss']
})
export class AccountComponent implements OnInit {
  readonly shopAddress = environment.shopAddress;
  user: UserProfile | null = null;
  activeTab: 'profile' | 'addresses' | 'orders' | 'pets' = 'profile';
  showLogoutConfirm = false;

  // Pets
  pets: PetProfile[] = [];
  isLoadingPets = false;

  // Edit Email Modal
  showEditEmailModal = false;
  newEmail = '';
  isUpdatingEmail = false;
  emailErrorMessage = '';
  emailSuccessMessage = '';

  // Delete Account Modal
  showDeleteAccountModal = false;
  isDeletingAccount = false;
  deleteErrorMessage = '';

  // Re-Order State
  isReordering: string | null = null;
  orderActionMessage = '';

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
    postalCode: '',
    isDefault: false
  };
  addressSaveMessage = '';

  // Popular Trivandrum neighbourhoods
  trivandrumLocations = [
    { name: 'Pattom Junction', lat: 8.5244, lon: 76.9442, pin: '695004' },
    { name: 'Kowdiar Palace', lat: 8.5241, lon: 76.9602, pin: '695003' },
    { name: 'Ambalamukku', lat: 8.5320, lon: 76.9550, pin: '695005' },
    { name: 'Palayam / Statue', lat: 8.5020, lon: 76.9500, pin: '695001' },
    { name: 'Sasthamangalam', lat: 8.5140, lon: 76.9740, pin: '695010' },
    { name: 'Technopark / Kazhakkoottam', lat: 8.5583, lon: 76.8812, pin: '695581' }
  ];

  // Orders
  orders: CustomerOrderSummary[] = [];
  isLoadingOrders = false;

  constructor(
    private authService: AuthService,
    private cartService: CartService,
    private petService: PetService,
    private http: HttpClient,
    private router: Router,
    private route: ActivatedRoute
  ) { }

  ngOnInit(): void {
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: '/account' } });
      return;
    }

    this.route.queryParams.subscribe(params => {
      if (params['tab'] === 'orders') {
        this.activeTab = 'orders';
      } else if (params['tab'] === 'addresses') {
        this.activeTab = 'addresses';
      } else if (params['tab'] === 'profile') {
        this.activeTab = 'profile';
      }
    });

    this.user = this.authService.getCurrentUser();

    // Fetch fresh profile from backend
    this.authService.fetchCurrentProfile().subscribe({
      next: () => {
        this.user = this.authService.getCurrentUser();
      }
    });

    this.loadAddresses();
    this.loadOrders();
    this.loadPets();
  }

  loadPets(): void {
    this.isLoadingPets = true;
    this.petService.getPets().subscribe({
      next: res => {
        this.isLoadingPets = false;
        if (res?.success && res.data) {
          this.pets = res.data;
        }
      },
      error: () => {
        this.isLoadingPets = false;
      }
    });
  }

  getSpeciesIcon(type: string): string {
    switch (type?.toLowerCase()) {
      case 'dog': return '🐶';
      case 'cat': return '🐱';
      case 'bird': return '🦜';
      case 'fish': return '🐠';
      case 'small animal': return '🐰';
      default: return '🐾';
    }
  }

  setTab(tab: 'profile' | 'addresses' | 'orders' | 'pets'): void {
    this.activeTab = tab;
    if (tab === 'pets' && this.pets.length === 0) {
      this.loadPets();
    }
  }

  // --- Edit Email ---
  openEditEmailModal(): void {
    this.newEmail = this.user?.email || '';
    this.emailErrorMessage = '';
    this.emailSuccessMessage = '';
    this.showEditEmailModal = true;
  }

  closeEditEmailModal(): void {
    this.showEditEmailModal = false;
  }

  submitEmailUpdate(): void {
    const trimmed = this.newEmail.trim().toLowerCase();
    if (!trimmed || !trimmed.includes('@') || !trimmed.includes('.')) {
      this.emailErrorMessage = 'Please enter a valid email address.';
      return;
    }
    if (trimmed === this.user?.email?.toLowerCase()) {
      this.closeEditEmailModal();
      return;
    }

    this.isUpdatingEmail = true;
    this.emailErrorMessage = '';

    this.http.put<any>(`${environment.apiUrl}/customer/email`, { email: trimmed }).subscribe({
      next: res => {
        this.isUpdatingEmail = false;
        if (res?.success) {
          const authData = res.data;
          const updatedProfile: UserProfile = {
            email: authData?.email || trimmed,
            firstName: authData?.firstName || this.user?.firstName || '',
            lastName: authData?.lastName || this.user?.lastName || '',
            phone: this.user?.phone || '',
            roles: authData?.roles || this.user?.roles || ['ROLE_CUSTOMER']
          };
          this.authService.updateCurrentUser(updatedProfile, authData?.token);
          this.user = updatedProfile;
          this.closeEditEmailModal();
          this.emailSuccessMessage = 'Email updated successfully!';
          setTimeout(() => this.emailSuccessMessage = '', 4000);
        } else {
          this.emailErrorMessage = res?.message || 'Failed to update email.';
        }
      },
      error: err => {
        this.isUpdatingEmail = false;
        this.emailErrorMessage = err.error?.message || 'Failed to update email address. Please try again.';
      }
    });
  }

  // --- Delete Account ---
  openDeleteAccountModal(): void {
    this.deleteErrorMessage = '';
    this.showDeleteAccountModal = true;
  }

  closeDeleteAccountModal(): void {
    this.showDeleteAccountModal = false;
  }

  confirmDeleteAccount(): void {
    this.isDeletingAccount = true;
    this.deleteErrorMessage = '';

    this.http.delete<any>(`${environment.apiUrl}/customer/account`).subscribe({
      next: () => {
        this.isDeletingAccount = false;
        this.showDeleteAccountModal = false;
        this.authService.logout();
        this.router.navigate(['/login'], { queryParams: { deleted: 'true' } });
      },
      error: err => {
        this.isDeletingAccount = false;
        this.deleteErrorMessage = err.error?.message || 'Failed to delete account. Please try again or contact store support.';
      }
    });
  }

  // --- Re-Order Functionality ---
  reorder(order: CustomerOrderSummary): void {
    this.isReordering = order.orderNumber;
    this.orderActionMessage = '';

    this.http.get<any>(`${environment.apiUrl}/customer/orders/${order.orderNumber}`).subscribe({
      next: res => {
        const orderDetail = res?.data;
        const items = orderDetail?.items || [];
        if (items.length === 0) {
          this.isReordering = null;
          alert('No items found in this order to re-order.');
          return;
        }

        let addedCount = 0;
        let completed = 0;

        for (const item of items) {
          if (item.productId) {
            this.cartService.addItem(
              item.productId,
              undefined,
              item.quantity || 1,
              {
                productName: item.productName,
                brandName: item.brandName,
                unitPrice: item.unitPrice
              }
            ).subscribe({
              next: () => {
                addedCount++;
                completed++;
                if (completed === items.length) {
                  this.finishReorder(order.orderNumber, addedCount);
                }
              },
              error: () => {
                completed++;
                if (completed === items.length) {
                  this.finishReorder(order.orderNumber, addedCount);
                }
              }
            });
          } else {
            completed++;
            if (completed === items.length) {
              this.finishReorder(order.orderNumber, addedCount);
            }
          }
        }
      },
      error: () => {
        this.isReordering = null;
        alert('Could not fetch order items for re-ordering.');
      }
    });
  }

  private finishReorder(orderNumber: string, count: number): void {
    this.isReordering = null;
    this.router.navigate(['/cart']);
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
      this.addressForm.landmark = selected.name;
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

  requestLogout(): void {
    this.showLogoutConfirm = true;
  }

  cancelLogout(): void {
    this.showLogoutConfirm = false;
  }

  confirmLogout(): void {
    this.showLogoutConfirm = false;
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  logout(): void {
    this.requestLogout();
  }
}
