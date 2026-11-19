import { Component, OnInit, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { BackButtonComponent } from '../../components/back-button/back-button.component';

declare const L: any;

export interface DeliveryEligibilityResult {
  checked: boolean;
  isEligible: boolean;
  distanceKm: number;
  deliveryRadiusKm: number;
  formattedAddress: string;
  message: string;
  deliveryCharge?: number;
  estimatedRoadDistanceKm?: number;
  estimatedDurationMinutes?: number;
}

@Component({
  selector: 'app-delivery',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, BackButtonComponent],
  templateUrl: './delivery.component.html',
  styleUrls: ['./delivery.component.scss']
})
export class DeliveryComponent implements OnInit, AfterViewInit, OnDestroy {
  // Store Location Details (Retrieved dynamically from backend database)
  shopCoordinates = {
    name: 'NOVA PETZLIFE',
    lat: 8.528448,
    lng: 76.953268,
    address: 'Thiruvananthapuram, Kerala 695003',
    maxRadiusKm: 20.0
  };

  // Full Address Inputs
  addressForm = {
    fullAddress: '',
    buildingOrHouse: '',
    streetArea: '',
    landmark: '',
    city: 'Thiruvananthapuram',
    state: 'Kerala',
    pincode: ''
  };

  isCheckingDelivery: boolean = false;
  showDetailedFields: boolean = false;
  deliveryResult: DeliveryEligibilityResult | null = null;
  errorMessage: string | null = null;

  // Selected Pin Coordinates
  selectedPin: { lat: number; lng: number } | null = null;

  // Leaflet Map References
  private map: any = null;
  private shopMarker: any = null;
  private deliveryCircle: any = null;
  private customerMarker: any = null;
  private distancePolyline: any = null;

  constructor(
    private http: HttpClient,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.fetchShopInfo();

    this.route.queryParams.subscribe(params => {
      if (params['address'] || params['area']) {
        this.addressForm.fullAddress = params['address'] || params['area'];
        setTimeout(() => this.checkDeliveryAvailability(), 300);
      }
    });

    this.route.fragment.subscribe(fragment => {
      if (fragment) {
        setTimeout(() => {
          const el = document.getElementById(fragment);
          if (el) {
            el.scrollIntoView({ behavior: 'smooth', block: 'start' });
          }
        }, 200);
      }
    });
  }

  ngAfterViewInit(): void {
    this.initLeafletMap();
  }

  ngOnDestroy(): void {
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
  }

  /**
   * Fetch dynamic store location & configured radius from backend
   */
  private fetchShopInfo(): void {
    this.http.get<any>(`${environment.apiUrl}/delivery/shop-info`).subscribe({
      next: (res) => {
        if (res?.success && res.data) {
          const d = res.data;
          this.shopCoordinates = {
            name: d.shopName || this.shopCoordinates.name,
            lat: Number(d.latitude) || this.shopCoordinates.lat,
            lng: Number(d.longitude) || this.shopCoordinates.lng,
            address: d.addressLine ? `${d.addressLine}, ${d.city || ''} ${d.pinCode || ''}` : this.shopCoordinates.address,
            maxRadiusKm: Number(d.deliveryRadiusKm) || 20.0
          };
          this.updateMapShopLayer();
        }
      },
      error: () => {}
    });
  }

  /**
   * Initialize interactive Leaflet Map with OpenStreetMap tiles
   */
  private initLeafletMap(): void {
    if (typeof L === 'undefined') {
      setTimeout(() => this.initLeafletMap(), 250);
      return;
    }

    const container = document.getElementById('delivery-interactive-map');
    if (!container || this.map) return;

    this.map = L.map('delivery-interactive-map', {
      center: [this.shopCoordinates.lat, this.shopCoordinates.lng],
      zoom: 12,
      scrollWheelZoom: true,
      zoomControl: true
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a> contributors'
    }).addTo(this.map);

    this.updateMapShopLayer();

    this.map.on('click', (e: any) => {
      this.onMapClickSetLocation(e.latlng.lat, e.latlng.lng);
    });

    setTimeout(() => {
      if (this.map) this.map.invalidateSize();
    }, 300);
  }

  /**
   * Render shop marker and 20 km coverage circle
   */
  private updateMapShopLayer(): void {
    if (!this.map || typeof L === 'undefined') return;

    const shopIcon = L.divIcon({
      className: 'leaflet-shop-marker',
      html: `
        <div class="shop-marker-pin">
          <div class="pin-bubble">🏪</div>
          <div class="pin-pulse"></div>
          <div class="pin-title">${this.shopCoordinates.name}</div>
        </div>
      `,
      iconSize: [44, 44],
      iconAnchor: [22, 40],
      popupAnchor: [0, -40]
    });

    if (this.shopMarker) {
      this.shopMarker.setLatLng([this.shopCoordinates.lat, this.shopCoordinates.lng]);
    } else {
      this.shopMarker = L.marker([this.shopCoordinates.lat, this.shopCoordinates.lng], {
        icon: shopIcon,
        zIndexOffset: 1000
      }).addTo(this.map);

      this.shopMarker.bindPopup(`
        <div class="map-popup-card">
          <h4>🏪 ${this.shopCoordinates.name}</h4>
          <p>${this.shopCoordinates.address}</p>
          <span class="badge-store">Official Store Hub</span>
        </div>
      `);
    }

    const radiusMeters = this.shopCoordinates.maxRadiusKm * 1000;
    if (this.deliveryCircle) {
      this.deliveryCircle.setLatLng([this.shopCoordinates.lat, this.shopCoordinates.lng]);
      this.deliveryCircle.setRadius(radiusMeters);
    } else {
      this.deliveryCircle = L.circle([this.shopCoordinates.lat, this.shopCoordinates.lng], {
        radius: radiusMeters,
        color: '#248B58',
        weight: 2,
        opacity: 0.85,
        fillColor: '#C8EBD1',
        fillOpacity: 0.18,
        dashArray: '6, 6'
      }).addTo(this.map);

      this.deliveryCircle.bindTooltip(`${this.shopCoordinates.maxRadiusKm} km Delivery Coverage Boundary`, {
        permanent: false,
        direction: 'top'
      });
    }
  }

  /**
   * Handle Map Click to place customer pin and reverse geocode
   */
  private onMapClickSetLocation(lat: number, lng: number): void {
    this.reverseGeocode(lat, lng, (address) => {
      this.addressForm.fullAddress = address;
      this.verifyCoordinates(lat, lng, address);
    });
  }

  /**
   * Main Check Delivery Availability action
   * Communicates directly with backend POST /api/v1/delivery/check-address
   */
  checkDeliveryAvailability(): void {
    this.errorMessage = null;

    const hasFull = !!this.addressForm.fullAddress && this.addressForm.fullAddress.trim().length > 0;
    const hasDetailed = (!!this.addressForm.streetArea && this.addressForm.streetArea.trim().length > 0) ||
                        (!!this.addressForm.pincode && this.addressForm.pincode.trim().length > 0);

    if (!hasFull && !hasDetailed) {
      this.errorMessage = 'Please enter your delivery address, street, or PIN code to check availability.';
      return;
    }

    this.isCheckingDelivery = true;

    const payload = {
      fullAddress: this.addressForm.fullAddress,
      buildingOrHouse: this.addressForm.buildingOrHouse,
      streetArea: this.addressForm.streetArea,
      landmark: this.addressForm.landmark,
      city: this.addressForm.city,
      state: this.addressForm.state,
      pincode: this.addressForm.pincode,
      latitude: this.selectedPin ? this.selectedPin.lat : null,
      longitude: this.selectedPin ? this.selectedPin.lng : null,
      orderSubtotal: 0
    };

    this.http.post<any>(`${environment.apiUrl}/delivery/check-address`, payload).subscribe({
      next: (res) => {
        this.isCheckingDelivery = false;
        if (res?.success && res.data) {
          const d = res.data;
          const dist = d.distanceKm != null ? Number(d.distanceKm) : 0;
          const radius = d.deliveryRadiusKm != null ? Number(d.deliveryRadiusKm) : this.shopCoordinates.maxRadiusKm;
          const isEligible = Boolean(d.isEligible);
          const roadDist = d.estimatedRoadDistanceKm != null ? Number(d.estimatedRoadDistanceKm) : this.calculateRoadDistance(dist);
          const duration = d.estimatedDurationMinutes != null ? Number(d.estimatedDurationMinutes) : this.calculateDurationMinutes(roadDist);

          let userMessage = d.message;
          if (isEligible) {
            userMessage = `Great! Delivery is available at your location.`;
          } else if (dist > radius) {
            userMessage = `Sorry, delivery is currently unavailable at this location. Our delivery service is available within a ${radius} km radius of our store.`;
          }

          this.deliveryResult = {
            checked: true,
            isEligible,
            distanceKm: dist,
            deliveryRadiusKm: radius,
            formattedAddress: this.addressForm.fullAddress || `${this.addressForm.buildingOrHouse} ${this.addressForm.streetArea}, ${this.addressForm.city} ${this.addressForm.pincode}`.trim(),
            message: userMessage,
            deliveryCharge: d.deliveryCharge != null ? Number(d.deliveryCharge) : undefined,
            estimatedRoadDistanceKm: roadDist,
            estimatedDurationMinutes: duration
          };

          if (this.selectedPin) {
            this.placeCustomerMarker(this.selectedPin.lat, this.selectedPin.lng, this.deliveryResult.formattedAddress, isEligible, dist, roadDist, duration);
          }
        } else {
          this.errorMessage = res?.message || 'Unable to resolve address. Please provide a more complete address.';
        }
      },
      error: (err) => {
        this.isCheckingDelivery = false;
        const msg = err.error?.message || 'Could not verify address. Please ensure street name and PIN code are provided.';
        this.errorMessage = msg;
      }
    });
  }

  /**
   * Verify direct coordinates (e.g. from GPS or Map Click)
   */
  private verifyCoordinates(lat: number, lng: number, addressLabel: string): void {
    this.selectedPin = { lat, lng };
    this.errorMessage = null;
    this.isCheckingDelivery = true;

    const payload = {
      fullAddress: addressLabel,
      latitude: lat,
      longitude: lng,
      orderSubtotal: 0
    };

    this.http.post<any>(`${environment.apiUrl}/delivery/check-address`, payload).subscribe({
      next: (res) => {
        this.isCheckingDelivery = false;
        if (res?.success && res.data) {
          const d = res.data;
          const dist = d.distanceKm != null ? Number(d.distanceKm) : 0;
          const radius = d.deliveryRadiusKm != null ? Number(d.deliveryRadiusKm) : this.shopCoordinates.maxRadiusKm;
          const isEligible = Boolean(d.isEligible);
          const roadDist = d.estimatedRoadDistanceKm != null ? Number(d.estimatedRoadDistanceKm) : this.calculateRoadDistance(dist);
          const duration = d.estimatedDurationMinutes != null ? Number(d.estimatedDurationMinutes) : this.calculateDurationMinutes(roadDist);

          let userMessage = isEligible
            ? `Great! Delivery is available at your location.`
            : `Sorry, delivery is currently unavailable at this location. Our delivery service is available within a ${radius} km radius of our store.`;

          this.deliveryResult = {
            checked: true,
            isEligible,
            distanceKm: dist,
            deliveryRadiusKm: radius,
            formattedAddress: addressLabel,
            message: userMessage,
            deliveryCharge: d.deliveryCharge != null ? Number(d.deliveryCharge) : undefined,
            estimatedRoadDistanceKm: roadDist,
            estimatedDurationMinutes: duration
          };

          this.placeCustomerMarker(lat, lng, addressLabel, isEligible, dist, roadDist, duration);
        }
      },
      error: () => {
        this.isCheckingDelivery = false;
      }
    });
  }

  /**
   * Use Device GPS only on explicit user click with permissions
   */
  useCurrentLocation(): void {
    if (!navigator.geolocation) {
      alert('Geolocation is not supported by your browser.');
      return;
    }

    this.isCheckingDelivery = true;
    this.errorMessage = null;

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const lat = pos.coords.latitude;
        const lng = pos.coords.longitude;
        this.selectedPin = { lat, lng };
        this.reverseGeocode(lat, lng, (address) => {
          this.addressForm.fullAddress = address;
          this.verifyCoordinates(lat, lng, address);
        });
      },
      (err) => {
        this.isCheckingDelivery = false;
        this.errorMessage = 'Could not access GPS. Please enter your delivery address manually below.';
      },
      { timeout: 10000, enableHighAccuracy: true }
    );
  }

  /**
   * Place/Update Customer Draggable Pin on Map with Distance Line
   */
  private placeCustomerMarker(
    lat: number,
    lng: number,
    address: string,
    isEligible: boolean,
    distanceKm: number,
    roadDistKm?: number,
    durationMins?: number
  ): void {
    if (!this.map || typeof L === 'undefined') return;

    const roadDist = roadDistKm != null ? roadDistKm : this.calculateRoadDistance(distanceKm);
    const duration = durationMins != null ? durationMins : this.calculateDurationMinutes(roadDist);

    const customerIcon = L.divIcon({
      className: 'leaflet-customer-marker',
      html: `
        <div class="customer-marker-pin ${isEligible ? 'eligible' : 'ineligible'}">
          <div class="pin-bubble">${isEligible ? '📍' : '⚠️'}</div>
          <div class="pin-distance">${distanceKm.toFixed(1)} km</div>
        </div>
      `,
      iconSize: [42, 42],
      iconAnchor: [21, 38],
      popupAnchor: [0, -38]
    });

    if (this.customerMarker) {
      this.customerMarker.setLatLng([lat, lng]);
      this.customerMarker.setIcon(customerIcon);
    } else {
      this.customerMarker = L.marker([lat, lng], {
        icon: customerIcon,
        draggable: true,
        zIndexOffset: 2000
      }).addTo(this.map);

      this.customerMarker.on('dragend', (e: any) => {
        const pos = e.target.getLatLng();
        this.onMapClickSetLocation(pos.lat, pos.lng);
      });
    }

    const popupHtml = `
      <div class="map-popup-card">
        <h4>${isEligible ? '✓ Within Delivery Zone' : '✕ Outside Delivery Zone'}</h4>
        <p><strong>${address}</strong></p>
        <p>Distance from store: <strong>${distanceKm.toFixed(1)} km</strong></p>
        <small>${isEligible ? '⚡ Local doorstep delivery available' : '⚠️ Exceeds store delivery radius'}</small>
      </div>
    `;
    this.customerMarker.bindPopup(popupHtml).openPopup();

    if (this.distancePolyline) {
      this.distancePolyline.setLatLngs([
        [this.shopCoordinates.lat, this.shopCoordinates.lng],
        [lat, lng]
      ]);
      this.distancePolyline.setStyle({
        color: isEligible ? '#248B58' : '#DC2626'
      });
    } else {
      this.distancePolyline = L.polyline([
        [this.shopCoordinates.lat, this.shopCoordinates.lng],
        [lat, lng]
      ], {
        color: isEligible ? '#248B58' : '#DC2626',
        weight: 3,
        dashArray: '6, 8',
        opacity: 0.9
      }).addTo(this.map);
    }

    const bounds = L.latLngBounds([
      [this.shopCoordinates.lat, this.shopCoordinates.lng],
      [lat, lng]
    ]);
    this.map.flyToBounds(bounds, {
      padding: [50, 50],
      maxZoom: 14,
      duration: 1.0
    });
  }

  private reverseGeocode(lat: number, lng: number, callback: (address: string) => void): void {
    const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}`;
    this.http.get<any>(url).subscribe({
      next: (res) => {
        if (res?.display_name) {
          const parts = res.display_name.split(',').slice(0, 3).join(', ');
          callback(parts);
        } else {
          callback(`Selected Location (${lat.toFixed(4)}, ${lng.toFixed(4)})`);
        }
      },
      error: () => {
        callback(`Selected Location (${lat.toFixed(4)}, ${lng.toFixed(4)})`);
      }
    });
  }

  resetMapToShop(): void {
    if (!this.map) return;
    this.map.flyTo([this.shopCoordinates.lat, this.shopCoordinates.lng], 13, { duration: 0.8 });
  }

  calculateRoadDistance(straightLineKm: number): number {
    if (straightLineKm <= 0) return 0;
    const factor = straightLineKm <= 3.0 ? 1.20 : straightLineKm <= 10.0 ? 1.24 : 1.28;
    return Number((straightLineKm * factor).toFixed(1));
  }

  calculateDurationMinutes(roadDistKm: number): number {
    if (roadDistKm <= 0) return 5;
    return Math.round(5 + (roadDistKm / 24.0) * 60.0);
  }
}
