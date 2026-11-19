import { Component, OnInit, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { BackButtonComponent } from '../../components/back-button/back-button.component';
import { PaginationComponent } from '../../components/pagination/pagination.component';

declare const L: any;

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
  googlePlaceId?: string;
  directionsUrl?: string;
}

export interface DeliverySlotData {
  id?: number;
  slotName: string;
  startTime: string;
  endTime: string;
  cutoffMinutes: number;
  maxOrders: number;
  isActive: boolean;
  availableDays?: string;
}

export interface StoreStatusData {
  isShopOpen: boolean;
  shopOpenTime: string;
  shopCloseTime: string;
  sundayOpenTime: string;
  sundayCloseTime: string;
  isDeliveryActive: boolean;
  deliveryStartTime: string;
  deliveryEndTime: string;
  deliverySpeedNotice: string;
  storeTimingsSummary: string;
}

@Component({
  selector: 'app-delivery-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, BackButtonComponent, PaginationComponent],
  templateUrl: './delivery-settings.component.html',
  styleUrls: ['./delivery-settings.component.scss']
})
export class DeliverySettingsComponent implements OnInit, AfterViewInit, OnDestroy {
  // Slots pagination state
  slotPage = 0;
  slotPageSize = 10;

  get paginatedSlots(): DeliverySlotData[] {
    const start = this.slotPage * this.slotPageSize;
    return this.slots.slice(start, start + this.slotPageSize);
  }

  get totalSlotElements(): number {
    return this.slots.length;
  }

  onSlotPageChange(page: number): void {
    this.slotPage = page;
  }

  onSlotPageSizeChange(size: number): void {
    this.slotPageSize = size;
    this.slotPage = 0;
  }
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

  storeStatus: StoreStatusData = {
    isShopOpen: true,
    shopOpenTime: '09:00 AM',
    shopCloseTime: '08:30 PM',
    sundayOpenTime: '10:00 AM',
    sundayCloseTime: '06:00 PM',
    isDeliveryActive: true,
    deliveryStartTime: '09:30 AM',
    deliveryEndTime: '08:00 PM',
    deliverySpeedNotice: 'Express Delivery within 2 - 3 Hours in Trivandrum',
    storeTimingsSummary: 'Mon - Sat: 09:00 AM - 08:30 PM | Sun: 10:00 AM - 06:00 PM'
  };
  isSavingStoreStatus = false;

  slots: DeliverySlotData[] = [];
  isLoading = false;
  isSaving = false;
  successMessage = '';
  errorMessage = '';

  // Edit Shop Location Modal State
  isEditingLocation = false;
  editForm = {
    shopName: '',
    addressLine: '',
    city: 'Thiruvananthapuram',
    state: 'Kerala',
    pinCode: '695003',
    latitude: 8.5284480,
    longitude: 76.9532680,
    deliveryRadiusKm: 20.0,
    googlePlaceId: '',
    directionsUrl: ''
  };

  // Known sample locations in Thiruvananthapuram for quick preset switching
  readonly locationPresets = [
    {
      name: 'Kuravankonam Store (Official)',
      lat: 8.528448,
      lon: 76.953268,
      address: 'Petzlife, Kuravankonam Junction, Kuravankonam Ambalamukku Road',
      pin: '695003'
    },
    {
      name: 'Kowdiar Junction',
      lat: 8.524000,
      lon: 76.965000,
      address: 'Near Kowdiar Palace / Kowdiar Junction',
      pin: '695003'
    },
    {
      name: 'Pattom Junction',
      lat: 8.526000,
      lon: 76.942000,
      address: 'Pattom Junction, Medical College Road',
      pin: '695004'
    },
    {
      name: 'East Fort / Palayam',
      lat: 8.503000,
      lon: 76.951000,
      address: 'Central Thiruvananthapuram, Palayam',
      pin: '695001'
    },
    {
      name: 'Technopark Phase 1',
      lat: 8.558000,
      lon: 76.881000,
      address: 'Technopark Main Gate, Kazhakoottam',
      pin: '695581'
    }
  ];

  // Leaflet Map Handles
  private viewMap: any = null;
  private viewMarker: any = null;
  private viewCircle: any = null;

  private editMap: any = null;
  private editMarker: any = null;
  private editCircle: any = null;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchData();
  }

  ngAfterViewInit(): void {
    this.initViewMap();
  }

  ngOnDestroy(): void {
    this.destroyViewMap();
    this.destroyEditMap();
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
          this.shopLocation.city = d.city || this.shopLocation.city;
          this.shopLocation.state = d.state || this.shopLocation.state;
          this.shopLocation.pinCode = d.pinCode || this.shopLocation.pinCode;
          this.shopLocation.latitude = Number(d.latitude) || 8.5284480;
          this.shopLocation.longitude = Number(d.longitude) || 76.9532680;
          this.shopLocation.deliveryRadiusKm = Number(d.deliveryRadiusKm) || 20.0;
          this.shopLocation.isVerified = !!d.isVerified;

          if (d.settings) {
            this.deliverySettings.deliveryCharge = d.settings.deliveryCharge;
            this.deliverySettings.freeDeliveryThreshold = d.settings.freeDeliveryThreshold;
            this.deliverySettings.minOrderValue = d.settings.minOrderValue;
          }

          this.updateViewMap();
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

    this.fetchStoreStatus();
  }

  fetchStoreStatus(): void {
    this.http.get<any>(`${environment.apiUrl}/settings/store-status`).subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.storeStatus = { ...this.storeStatus, ...res.data };
        }
      }
    });
  }

  saveStoreStatus(): void {
    this.isSavingStoreStatus = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.http.put<any>(`${environment.apiUrl}/admin/settings/store-status`, this.storeStatus).subscribe({
      next: res => {
        this.isSavingStoreStatus = false;
        this.successMessage = 'Store operational hours & live delivery status updated successfully!';
        if (res?.success && res.data) {
          this.storeStatus = { ...this.storeStatus, ...res.data };
        }
        setTimeout(() => this.successMessage = '', 4000);
      },
      error: err => {
        this.isSavingStoreStatus = false;
        this.errorMessage = err.error?.message || 'Failed to update store operational hours.';
      }
    });
  }

  toggleShopOpen(): void {
    this.storeStatus.isShopOpen = !this.storeStatus.isShopOpen;
    this.saveStoreStatus();
  }

  toggleDeliveryActive(): void {
    this.storeStatus.isDeliveryActive = !this.storeStatus.isDeliveryActive;
    this.saveStoreStatus();
  }

  /**
   * Main Read-only Map Preview on Settings Card
   */
  private initViewMap(): void {
    if (typeof L === 'undefined') {
      setTimeout(() => this.initViewMap(), 300);
      return;
    }
    const container = document.getElementById('admin-overview-map');
    if (!container || this.viewMap) return;

    this.viewMap = L.map('admin-overview-map', {
      center: [this.shopLocation.latitude, this.shopLocation.longitude],
      zoom: 12,
      scrollWheelZoom: false
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    }).addTo(this.viewMap);

    this.updateViewMap();
  }

  private updateViewMap(): void {
    if (!this.viewMap || typeof L === 'undefined') return;

    const lat = this.shopLocation.latitude;
    const lng = this.shopLocation.longitude;
    const radiusMeters = this.shopLocation.deliveryRadiusKm * 1000;

    const shopIcon = L.divIcon({
      className: 'admin-marker-pin',
      html: '<div class="pin-hub">🏪<span class="hub-text">STORE</span></div>',
      iconSize: [40, 40],
      iconAnchor: [20, 38]
    });

    if (this.viewMarker) {
      this.viewMarker.setLatLng([lat, lng]);
    } else {
      this.viewMarker = L.marker([lat, lng], { icon: shopIcon }).addTo(this.viewMap);
      this.viewMarker.bindPopup(`<strong>${this.shopLocation.shopName}</strong><br>${this.shopLocation.addressLine}`);
    }

    if (this.viewCircle) {
      this.viewCircle.setLatLng([lat, lng]);
      this.viewCircle.setRadius(radiusMeters);
    } else {
      this.viewCircle = L.circle([lat, lng], {
        radius: radiusMeters,
        color: '#248B58',
        fillColor: '#C8EBD1',
        fillOpacity: 0.18,
        weight: 2,
        dashArray: '5, 5'
      }).addTo(this.viewMap);
    }

    this.viewMap.setView([lat, lng], 12);
  }

  private destroyViewMap(): void {
    if (this.viewMap) {
      this.viewMap.remove();
      this.viewMap = null;
    }
  }

  /**
   * Edit Modal Interactive Map
   */
  openEditLocation(): void {
    this.editForm = {
      shopName: this.shopLocation.shopName,
      addressLine: this.shopLocation.addressLine,
      city: this.shopLocation.city || 'Thiruvananthapuram',
      state: this.shopLocation.state || 'Kerala',
      pinCode: this.shopLocation.pinCode || '695003',
      latitude: this.shopLocation.latitude,
      longitude: this.shopLocation.longitude,
      deliveryRadiusKm: this.shopLocation.deliveryRadiusKm,
      googlePlaceId: this.shopLocation.googlePlaceId || '',
      directionsUrl: this.shopLocation.directionsUrl || ''
    };
    this.isEditingLocation = true;
    this.errorMessage = '';

    setTimeout(() => {
      this.initEditMap();
    }, 250);
  }

  cancelEditLocation(): void {
    this.isEditingLocation = false;
    this.destroyEditMap();
  }

  private initEditMap(): void {
    if (typeof L === 'undefined') {
      setTimeout(() => this.initEditMap(), 200);
      return;
    }

    const container = document.getElementById('admin-edit-interactive-map');
    if (!container) return;

    if (this.editMap) {
      this.editMap.remove();
      this.editMap = null;
    }

    const lat = this.editForm.latitude;
    const lng = this.editForm.longitude;

    this.editMap = L.map('admin-edit-interactive-map', {
      center: [lat, lng],
      zoom: 13,
      scrollWheelZoom: true
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© OpenStreetMap contributors'
    }).addTo(this.editMap);

    const editIcon = L.divIcon({
      className: 'admin-marker-pin draggable',
      html: '<div class="pin-hub draggable">🏪<span class="hub-text">DRAG ME</span></div>',
      iconSize: [44, 44],
      iconAnchor: [22, 40]
    });

    // Draggable Store Marker
    this.editMarker = L.marker([lat, lng], {
      icon: editIcon,
      draggable: true
    }).addTo(this.editMap);

    this.editMarker.bindPopup(`<strong>Drag this marker or click the map</strong> to set the exact shop location`).openPopup();

    // Coverage Circle
    this.editCircle = L.circle([lat, lng], {
      radius: this.editForm.deliveryRadiusKm * 1000,
      color: '#248B58',
      fillColor: '#C8EBD1',
      fillOpacity: 0.22,
      weight: 2,
      dashArray: '6, 6'
    }).addTo(this.editMap);

    // Marker Drag Event
    this.editMarker.on('dragend', (e: any) => {
      const pos = e.target.getLatLng();
      this.updateLocationFromMap(pos.lat, pos.lng);
    });

    // Map Click Event
    this.editMap.on('click', (e: any) => {
      this.updateLocationFromMap(e.latlng.lat, e.latlng.lng);
    });

    setTimeout(() => {
      if (this.editMap) this.editMap.invalidateSize();
    }, 200);
  }

  private updateLocationFromMap(lat: number, lng: number): void {
    this.editForm.latitude = Number(lat.toFixed(6));
    this.editForm.longitude = Number(lng.toFixed(6));

    if (this.editMarker) {
      this.editMarker.setLatLng([lat, lng]);
    }
    if (this.editCircle) {
      this.editCircle.setLatLng([lat, lng]);
    }

    // Reverse geocode to suggest updated street address
    const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}`;
    this.http.get<any>(url).subscribe({
      next: res => {
        if (res?.display_name) {
          const parts = res.display_name.split(',').slice(0, 3).join(', ');
          // Only suggest if address line is empty or default
          if (!this.editForm.addressLine || this.editForm.addressLine.includes('Kuravankonam')) {
            this.editForm.addressLine = parts;
          }
        }
      },
      error: () => {}
    });
  }

  onRadiusInputChange(): void {
    if (!this.editCircle || !this.editMap) return;
    const r = (Number(this.editForm.deliveryRadiusKm) || 20) * 1000;
    this.editCircle.setRadius(r);
  }

  private destroyEditMap(): void {
    if (this.editMap) {
      this.editMap.remove();
      this.editMap = null;
    }
  }

  applyPreset(preset: { name: string; lat: number; lon: number; address: string; pin: string }): void {
    this.editForm.latitude = preset.lat;
    this.editForm.longitude = preset.lon;
    this.editForm.addressLine = preset.address;
    this.editForm.pinCode = preset.pin;

    if (this.editMap) {
      this.editMap.flyTo([preset.lat, preset.lon], 14, { duration: 0.8 });
      this.updateLocationFromMap(preset.lat, preset.lon);
    }
  }

  detectGpsLocation(): void {
    if (!navigator.geolocation) {
      alert('Geolocation is not supported by your browser.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      pos => {
        const lat = Number(pos.coords.latitude.toFixed(6));
        const lng = Number(pos.coords.longitude.toFixed(6));
        this.editForm.latitude = lat;
        this.editForm.longitude = lng;

        if (this.editMap) {
          this.editMap.flyTo([lat, lng], 15, { duration: 0.8 });
          this.updateLocationFromMap(lat, lng);
        }
      },
      err => {
        alert('Could not retrieve GPS coordinates: ' + err.message);
      },
      { timeout: 10000, enableHighAccuracy: true }
    );
  }

  saveLocation(): void {
    if (!this.editForm.shopName.trim() || !this.editForm.addressLine.trim()) {
      this.errorMessage = 'Please provide shop name and street address.';
      return;
    }
    if (!this.editForm.latitude || !this.editForm.longitude) {
      this.errorMessage = 'Valid latitude and longitude coordinates are required.';
      return;
    }

    this.isSaving = true;
    this.errorMessage = '';

    const payload = {
      shopName: this.editForm.shopName.trim(),
      addressLine: this.editForm.addressLine.trim(),
      city: this.editForm.city.trim(),
      state: this.editForm.state.trim(),
      pinCode: this.editForm.pinCode.trim(),
      googlePlaceId: this.editForm.googlePlaceId ? this.editForm.googlePlaceId.trim() : null,
      latitude: this.editForm.latitude,
      longitude: this.editForm.longitude,
      deliveryRadiusKm: this.editForm.deliveryRadiusKm,
      directionsUrl: this.editForm.directionsUrl ? this.editForm.directionsUrl.trim() : null
    };

    this.http.put<any>(`${environment.apiUrl}/admin/delivery/shop-location`, payload).subscribe({
      next: res => {
        this.isSaving = false;
        this.isEditingLocation = false;
        this.destroyEditMap();

        if (res?.success && res.data) {
          const d = res.data;
          this.shopLocation.shopName = d.shopName;
          this.shopLocation.addressLine = d.addressLine;
          this.shopLocation.city = d.city;
          this.shopLocation.state = d.state;
          this.shopLocation.pinCode = d.pinCode;
          this.shopLocation.latitude = Number(d.latitude);
          this.shopLocation.longitude = Number(d.longitude);
          this.shopLocation.deliveryRadiusKm = Number(d.deliveryRadiusKm);
          this.shopLocation.isVerified = !!d.isVerified;
        }

        this.updateViewMap();
        this.successMessage = 'Store location, coordinates & 20 km coverage boundary saved successfully!';
        setTimeout(() => this.successMessage = '', 5000);
      },
      error: err => {
        this.isSaving = false;
        this.errorMessage = err.error?.message || 'Failed to update store location on backend';
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

  // --- Delivery Slot Management CRUD ---
  showSlotModal = false;
  isEditingSlot = false;
  editingSlotId: number | null = null;
  slotModalError = '';
  slotForm: DeliverySlotData = {
    slotName: '',
    startTime: '09:00',
    endTime: '13:00',
    cutoffMinutes: 60,
    maxOrders: 20,
    isActive: true,
    availableDays: 'Monday,Tuesday,Wednesday,Thursday,Friday,Saturday'
  };

  fetchSlots(): void {
    this.http.get<any>(`${environment.apiUrl}/admin/delivery/slots`).subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.slots = res.data;
        }
      },
      error: () => {
        // Fallback to public endpoint
        this.http.get<any>(`${environment.apiUrl}/delivery/slots`).subscribe(res => {
          if (res?.success && res.data) {
            this.slots = res.data;
          }
        });
      }
    });
  }

  openCreateSlotModal(): void {
    this.isEditingSlot = false;
    this.editingSlotId = null;
    this.slotForm = {
      slotName: '',
      startTime: '09:00',
      endTime: '13:00',
      cutoffMinutes: 60,
      maxOrders: 20,
      isActive: true,
      availableDays: 'Monday,Tuesday,Wednesday,Thursday,Friday,Saturday'
    };
    this.slotModalError = '';
    this.showSlotModal = true;
  }

  openEditSlotModal(slot: DeliverySlotData): void {
    this.isEditingSlot = true;
    this.editingSlotId = slot.id || null;
    this.slotForm = {
      slotName: slot.slotName,
      startTime: slot.startTime,
      endTime: slot.endTime,
      cutoffMinutes: slot.cutoffMinutes,
      maxOrders: slot.maxOrders,
      isActive: slot.isActive,
      availableDays: slot.availableDays || 'Monday,Tuesday,Wednesday,Thursday,Friday,Saturday'
    };
    this.slotModalError = '';
    this.showSlotModal = true;
  }

  saveSlot(): void {
    if (!this.slotForm.slotName.trim()) {
      this.slotModalError = 'Slot name is required (e.g. Morning, Afternoon, Evening)';
      return;
    }
    if (!this.slotForm.startTime || !this.slotForm.endTime) {
      this.slotModalError = 'Start time and End time are required';
      return;
    }

    const payload = {
      slotName: this.slotForm.slotName.trim(),
      startTime: this.slotForm.startTime,
      endTime: this.slotForm.endTime,
      cutoffMinutes: Number(this.slotForm.cutoffMinutes) || 60,
      maxOrders: Number(this.slotForm.maxOrders) || 20,
      isActive: this.slotForm.isActive,
      availableDays: this.slotForm.availableDays
    };

    if (this.isEditingSlot && this.editingSlotId) {
      this.http.put<any>(`${environment.apiUrl}/admin/delivery/slots/${this.editingSlotId}`, payload).subscribe({
        next: res => {
          if (res?.success) {
            this.showSlotModal = false;
            this.fetchSlots();
            this.successMessage = 'Delivery window updated successfully!';
            setTimeout(() => this.successMessage = '', 4000);
          }
        },
        error: err => {
          this.slotModalError = err.error?.message || 'Failed to update delivery window';
        }
      });
    } else {
      this.http.post<any>(`${environment.apiUrl}/admin/delivery/slots`, payload).subscribe({
        next: res => {
          if (res?.success) {
            this.showSlotModal = false;
            this.fetchSlots();
            this.successMessage = 'New delivery window added successfully!';
            setTimeout(() => this.successMessage = '', 4000);
          }
        },
        error: err => {
          this.slotModalError = err.error?.message || 'Failed to create delivery window';
        }
      });
    }
  }

  toggleSlotActive(slot: DeliverySlotData): void {
    if (!slot.id) return;
    this.http.patch<any>(`${environment.apiUrl}/admin/delivery/slots/${slot.id}/status`, {}).subscribe({
      next: res => {
        if (res?.success) {
          slot.isActive = !slot.isActive;
          this.successMessage = `Delivery slot '${slot.slotName}' is now ${slot.isActive ? 'Active' : 'Inactive'}`;
          setTimeout(() => this.successMessage = '', 3000);
        }
      },
      error: err => {
        this.errorMessage = err.error?.message || 'Failed to toggle delivery slot status';
        setTimeout(() => this.errorMessage = '', 4000);
      }
    });
  }

  deleteSlot(slot: DeliverySlotData): void {
    if (!slot.id) return;
    const confirmed = confirm(`Are you sure you want to permanently delete delivery slot '${slot.slotName}' (${slot.startTime} - ${slot.endTime})?`);
    if (!confirmed) return;

    this.http.delete<any>(`${environment.apiUrl}/admin/delivery/slots/${slot.id}`).subscribe({
      next: res => {
        if (res?.success) {
          this.slots = this.slots.filter(s => s.id !== slot.id);
          this.successMessage = `Delivery slot '${slot.slotName}' was deleted.`;
          setTimeout(() => this.successMessage = '', 3000);
        }
      },
      error: err => {
        this.errorMessage = err.error?.message || 'Failed to delete delivery slot';
        setTimeout(() => this.errorMessage = '', 4000);
      }
    });
  }
}
