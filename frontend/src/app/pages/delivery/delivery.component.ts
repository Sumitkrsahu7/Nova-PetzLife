import { Component, OnInit, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

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
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './delivery.component.html',
  styleUrls: ['./delivery.component.scss']
})
export class DeliveryComponent implements OnInit, AfterViewInit, OnDestroy {
  // Store Location Details (Updated from backend API if online)
  shopCoordinates = {
    name: 'Petzlife (NOVA PETZLIFE)',
    lat: 8.528448,
    lng: 76.953268,
    address: 'Petzlife, Kuravankonam Junction, Kuravankonam Ambalamukku Road, Thiruvananthapuram, Kerala 695003',
    maxRadiusKm: 20.0
  };

  // Search & Detailed Address Inputs
  activeTab: 'search' | 'address' = 'search';
  customerSearchInput: string = '';
  
  detailedAddress = {
    buildingOrHouse: '',
    streetArea: '',
    landmark: '',
    city: 'Thiruvananthapuram',
    pincode: '695003'
  };

  isCheckingDelivery: boolean = false;
  isGeocoding: boolean = false;
  deliveryResult: DeliveryEligibilityResult | null = null;

  // Selected Pin Coordinates
  selectedPin: { lat: number; lng: number } | null = null;

  // Leaflet Map References
  private map: any = null;
  private shopMarker: any = null;
  private deliveryCircle: any = null;
  private customerMarker: any = null;
  private distancePolyline: any = null;

  // Curated database of known localities across Thiruvananthapuram with coordinates
  readonly knownTvmLocations: { [key: string]: { lat: number; lng: number; name: string } } = {
    'kuravankonam': { lat: 8.528448, lng: 76.953268, name: 'Kuravankonam' },
    'kowdiar': { lat: 8.5240, lng: 76.9650, name: 'Kowdiar' },
    'ambalamukku': { lat: 8.5330, lng: 76.9590, name: 'Ambalamukku' },
    'pattom': { lat: 8.5260, lng: 76.9420, name: 'Pattom' },
    'peroorkada': { lat: 8.5390, lng: 76.9740, name: 'Peroorkada' },
    'kesavadasapuram': { lat: 8.5350, lng: 76.9380, name: 'Kesavadasapuram' },
    'sasthamangalam': { lat: 8.5120, lng: 76.9690, name: 'Sasthamangalam' },
    'medical college': { lat: 8.5230, lng: 76.9280, name: 'Medical College' },
    'palayam': { lat: 8.5030, lng: 76.9510, name: 'Palayam' },
    'thampanoor': { lat: 8.4880, lng: 76.9530, name: 'Thampanoor' },
    'vazhuthacaud': { lat: 8.5000, lng: 76.9650, name: 'Vazhuthacaud' },
    'shangumugham': { lat: 8.4790, lng: 76.9110, name: 'Shangumugham' },
    'kazhakoottam': { lat: 8.5680, lng: 76.8710, name: 'Kazhakoottam' },
    'technopark': { lat: 8.5580, lng: 76.8810, name: 'Technopark' },
    'kovalam': { lat: 8.3980, lng: 76.9790, name: 'Kovalam' },
    'nedumangad': { lat: 8.6010, lng: 77.0010, name: 'Nedumangad' },
    'neyyattinkara': { lat: 8.4010, lng: 77.0860, name: 'Neyyattinkara' },
    'attingal': { lat: 8.6960, lng: 76.8140, name: 'Attingal' },
    'varkala': { lat: 8.7378, lng: 76.7163, name: 'Varkala' },
    'kariavattom': { lat: 8.5560, lng: 76.8820, name: 'Kariavattom' },
    'sreekaryam': { lat: 8.5480, lng: 76.9180, name: 'Sreekaryam' },
    'pettah': { lat: 8.4980, lng: 76.9320, name: 'Pettah' },
    'chacka': { lat: 8.4920, lng: 76.9190, name: 'Chacka' },
    'poojappura': { lat: 8.4910, lng: 76.9740, name: 'Poojappura' },
    'karamana': { lat: 8.4810, lng: 76.9630, name: 'Karamana' },
    'manacaud': { lat: 8.4730, lng: 76.9500, name: 'Manacaud' },
    'nemom': { lat: 8.4520, lng: 77.0010, name: 'Nemom' },
    // PIN codes
    '695003': { lat: 8.5284, lng: 76.9532, name: 'Kuravankonam / Kowdiar (695003)' },
    '695004': { lat: 8.5260, lng: 76.9420, name: 'Pattom (695004)' },
    '695001': { lat: 8.5030, lng: 76.9510, name: 'Palayam / GPO (695001)' },
    '695014': { lat: 8.5000, lng: 76.9650, name: 'Vazhuthacaud (695014)' },
    '695581': { lat: 8.5580, lng: 76.8810, name: 'Technopark (695581)' },
    '695582': { lat: 8.5680, lng: 76.8710, name: 'Kazhakoottam (695582)' },
    '695121': { lat: 8.4010, lng: 77.0860, name: 'Neyyattinkara (695121)' },
    '695101': { lat: 8.6960, lng: 76.8140, name: 'Attingal (695101)' }
  };

  constructor(
    private http: HttpClient,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.fetchShopInfo();

    this.route.queryParams.subscribe(params => {
      if (params['area']) {
        this.customerSearchInput = params['area'];
        setTimeout(() => this.searchAddressOrLocality(), 400);
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
   * Fetch current store location & configured radius from backend
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
      error: () => {
        // Fallback to default Kuravankonam coordinates
      }
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

    // Centered at shop
    this.map = L.map('delivery-interactive-map', {
      center: [this.shopCoordinates.lat, this.shopCoordinates.lng],
      zoom: 12,
      scrollWheelZoom: true,
      zoomControl: true
    });

    // High quality OpenStreetMap tiles
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a> contributors'
    }).addTo(this.map);

    this.updateMapShopLayer();

    // Click anywhere on map to drop or move customer pin
    this.map.on('click', (e: any) => {
      this.onMapClickSetLocation(e.latlng.lat, e.latlng.lng);
    });

    // Invalidate size once rendered
    setTimeout(() => {
      if (this.map) this.map.invalidateSize();
    }, 300);
  }

  /**
   * Render shop marker and 20 km coverage circle
   */
  private updateMapShopLayer(): void {
    if (!this.map || typeof L === 'undefined') return;

    // Shop Marker
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
          <span class="badge-store">Storefront & Dispatch Hub</span>
        </div>
      `);
    }

    // 20 km Coverage Circle (in meters)
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

      this.deliveryCircle.bindTooltip(`20 km Delivery Coverage Boundary`, {
        permanent: false,
        direction: 'top'
      });
    }
  }

  /**
   * Handle Map Click to place or move customer pin
   */
  private onMapClickSetLocation(lat: number, lng: number): void {
    this.reverseGeocode(lat, lng, (address) => {
      this.customerSearchInput = address;
      this.verifyLocation(lat, lng, address);
    });
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

    this.selectedPin = { lat, lng };
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

      // Handle marker drag end
      this.customerMarker.on('dragend', (e: any) => {
        const pos = e.target.getLatLng();
        this.onMapClickSetLocation(pos.lat, pos.lng);
      });
    }

    const popupHtml = `
      <div class="map-popup-card">
        <h4>${isEligible ? '✓ Within Delivery Zone' : '✕ Outside Delivery Zone'}</h4>
        <p><strong>${address}</strong></p>
        <p>Straight-line Distance: <strong>${distanceKm.toFixed(1)} km</strong></p>
        <p>Est. Road Route: <strong>~${roadDist.toFixed(1)} km</strong> (${duration} mins)</p>
        <small>${isEligible ? '⚡ Same-day doorstep delivery available from Kuravankonam hub' : '⚠️ Exceeds 20 km local delivery radius (In-store pickup available at Kuravankonam)'}</small>
      </div>
    `;
    this.customerMarker.bindPopup(popupHtml).openPopup();

    // Draw straight-line route from shop to customer pin
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

    // Pan map to keep both shop and customer marker visible
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

  /**
   * Search via Quick Input (Locality, Landmark, PIN code, or Address)
   */
  searchAddressOrLocality(): void {
    const query = this.customerSearchInput.trim();
    if (!query) return;

    this.isCheckingDelivery = true;
    const lower = query.toLowerCase();

    // 1. Check local TVM database first
    let matched: { lat: number; lng: number; name: string } | null = null;
    for (const [key, loc] of Object.entries(this.knownTvmLocations)) {
      if (lower.includes(key) || key.includes(lower)) {
        matched = loc;
        break;
      }
    }

    if (matched) {
      this.verifyLocation(matched.lat, matched.lng, matched.name);
      return;
    }

    // 2. Query OpenStreetMap Nominatim Geocoding API for full address/landmarks
    this.geocodeWithNominatim(query);
  }

  /**
   * Verify Complete Detailed Address
   */
  verifyDetailedStreetAddress(): void {
    const parts = [
      this.detailedAddress.buildingOrHouse,
      this.detailedAddress.streetArea,
      this.detailedAddress.landmark,
      this.detailedAddress.city,
      this.detailedAddress.pincode
    ].filter(p => !!p && p.trim().length > 0);

    if (parts.length === 0) {
      alert('Please fill in your street, area, or landmark to verify.');
      return;
    }

    const fullAddressQuery = parts.join(', ');
    this.customerSearchInput = fullAddressQuery;
    this.isCheckingDelivery = true;

    // First try Nominatim with full address query
    this.geocodeWithNominatim(fullAddressQuery);
  }

  /**
   * Geocode an address query using Nominatim OpenStreetMap API
   */
  private geocodeWithNominatim(addressQuery: string): void {
    this.isGeocoding = true;
    const tvmSuffix = addressQuery.toLowerCase().includes('thiruvananthapuram') || addressQuery.toLowerCase().includes('trivandrum')
      ? addressQuery
      : `${addressQuery}, Thiruvananthapuram, Kerala, India`;

    const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(tvmSuffix)}&limit=1`;

    this.http.get<any[]>(url).subscribe({
      next: (results) => {
        this.isGeocoding = false;
        if (results && results.length > 0) {
          const first = results[0];
          const lat = parseFloat(first.lat);
          const lng = parseFloat(first.lon);
          const label = first.display_name?.split(',').slice(0, 3).join(',') || addressQuery;
          this.verifyLocation(lat, lng, label);
        } else {
          // Fallback: check if query contains any known word
          this.fallbackFuzzyMatch(addressQuery);
        }
      },
      error: () => {
        this.isGeocoding = false;
        this.fallbackFuzzyMatch(addressQuery);
      }
    });
  }

  /**
   * Reverse Geocode (Coordinates -> Address Name)
   */
  private reverseGeocode(lat: number, lng: number, callback: (address: string) => void): void {
    // Check nearest known location first for instantaneous label
    let nearestLoc: string = 'Thiruvananthapuram';
    let minD = 999999;
    for (const loc of Object.values(this.knownTvmLocations)) {
      const d = this.haversineDistance(lat, lng, loc.lat, loc.lng);
      if (d < minD) {
        minD = d;
        nearestLoc = loc.name;
      }
    }

    const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}`;
    this.http.get<any>(url).subscribe({
      next: (res) => {
        if (res?.display_name) {
          const parts = res.display_name.split(',').slice(0, 3).join(', ');
          callback(parts);
        } else {
          callback(`Near ${nearestLoc} (${lat.toFixed(4)}, ${lng.toFixed(4)})`);
        }
      },
      error: () => {
        callback(`Near ${nearestLoc} (${lat.toFixed(4)}, ${lng.toFixed(4)})`);
      }
    });
  }

  /**
   * Fallback fuzzy search if Nominatim fails or has no exact match
   */
  private fallbackFuzzyMatch(query: string): void {
    const lower = query.toLowerCase();
    for (const [key, loc] of Object.entries(this.knownTvmLocations)) {
      if (lower.includes(key) || key.includes(lower)) {
        this.verifyLocation(loc.lat, loc.lng, loc.name);
        return;
      }
    }
    // Default to central TVM coordinates
    this.verifyLocation(8.503, 76.951, query);
  }

  /**
   * Verify delivery coordinates via backend API with client-side geodesic & road estimation fallback
   */
  private verifyLocation(lat: number, lng: number, addressLabel: string): void {
    this.isCheckingDelivery = true;

    // Calculate local geodesic distance immediately
    const distKm = this.haversineDistance(
      this.shopCoordinates.lat,
      this.shopCoordinates.lng,
      lat,
      lng
    );
    const localEligible = distKm <= this.shopCoordinates.maxRadiusKm;
    const localRoadDist = this.calculateRoadDistance(distKm);
    const localDuration = this.calculateDurationMinutes(localRoadDist);

    const requestBody = {
      latitude: lat,
      longitude: lng,
      orderSubtotal: 600
    };

    this.http.post<any>(`${environment.apiUrl}/delivery/check-eligibility`, requestBody).subscribe({
      next: (res) => {
        this.isCheckingDelivery = false;
        if (res?.success && res.data) {
          const d = res.data;
          const finalDist = d.distanceKm != null ? Number(d.distanceKm) : distKm;
          // Determine eligibility: strictly true if within 20 km boundary
          const withinRadius = finalDist <= this.shopCoordinates.maxRadiusKm;
          const isEligible = withinRadius && (d.isEligible !== false || withinRadius);
          const roadDist = d.estimatedRoadDistanceKm != null ? Number(d.estimatedRoadDistanceKm) : this.calculateRoadDistance(finalDist);
          const duration = d.estimatedDurationMinutes != null ? Number(d.estimatedDurationMinutes) : this.calculateDurationMinutes(roadDist);

          let message = d.message;
          if (withinRadius) {
            // Guarantee positive, clean message if backend had pending verification warning
            if (!message || message.includes('pending owner verification') || message.includes('exceeds')) {
              message = `Delivery is available! Your location is ${finalDist.toFixed(1)} km from our Kuravankonam storefront (est. road route ~${roadDist.toFixed(1)} km, approx. ${duration} mins transit), which is well within our ${this.shopCoordinates.maxRadiusKm} km local delivery zone.`;
            }
          } else {
            message = `Location is ${finalDist.toFixed(1)} km away, exceeding our ${this.shopCoordinates.maxRadiusKm} km local delivery radius. In-store pickup is always welcome at our Kuravankonam store.`;
          }

          this.deliveryResult = {
            checked: true,
            isEligible,
            distanceKm: finalDist,
            deliveryRadiusKm: this.shopCoordinates.maxRadiusKm,
            formattedAddress: addressLabel,
            message,
            deliveryCharge: d.deliveryCharge ? Number(d.deliveryCharge) : undefined,
            estimatedRoadDistanceKm: roadDist,
            estimatedDurationMinutes: duration
          };

          this.placeCustomerMarker(lat, lng, addressLabel, isEligible, finalDist, roadDist, duration);
        } else {
          this.applyLocalResult(lat, lng, addressLabel, distKm, localEligible);
        }
      },
      error: () => {
        this.isCheckingDelivery = false;
        this.applyLocalResult(lat, lng, addressLabel, distKm, localEligible);
      }
    });
  }

  private applyLocalResult(lat: number, lng: number, address: string, dist: number, eligible: boolean): void {
    const roadDist = this.calculateRoadDistance(dist);
    const duration = this.calculateDurationMinutes(roadDist);
    this.deliveryResult = {
      checked: true,
      isEligible: eligible,
      distanceKm: dist,
      deliveryRadiusKm: this.shopCoordinates.maxRadiusKm,
      formattedAddress: address,
      message: eligible
        ? `Great news! Your location is approximately ${dist.toFixed(1)} km from our Kuravankonam storefront (est. road route ~${roadDist.toFixed(1)} km, approx. ${duration} mins transit), which is well within our ${this.shopCoordinates.maxRadiusKm} km same-day delivery service area!`
        : `Location Outside Delivery Zone. Your selected address is approximately ${dist.toFixed(1)} km away, which exceeds our ${this.shopCoordinates.maxRadiusKm} km local delivery boundary.`,
      estimatedRoadDistanceKm: roadDist,
      estimatedDurationMinutes: duration
    };
    this.placeCustomerMarker(lat, lng, address, eligible, dist, roadDist, duration);
  }

  /**
   * Geolocation API: Use Device GPS
   */
  useCurrentLocation(): void {
    if (!navigator.geolocation) {
      alert('Geolocation is not supported by your browser.');
      return;
    }

    this.isCheckingDelivery = true;
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const lat = pos.coords.latitude;
        const lng = pos.coords.longitude;
        this.reverseGeocode(lat, lng, (address) => {
          this.customerSearchInput = address;
          this.verifyLocation(lat, lng, address);
        });
      },
      (err) => {
        this.isCheckingDelivery = false;
        alert('Could not detect your GPS location. Please check browser permissions or search by address/locality.');
      },
      { timeout: 10000, enableHighAccuracy: true }
    );
  }

  /**
   * Quick chip selection
   */
  selectLocality(areaName: string): void {
    this.customerSearchInput = areaName;
    this.searchAddressOrLocality();
  }

  /**
   * Reset map view back to Kuravankonam shop
   */
  resetMapToShop(): void {
    if (!this.map) return;
    this.map.flyTo([this.shopCoordinates.lat, this.shopCoordinates.lng], 13, { duration: 0.8 });
  }

  /**
   * Estimates urban road network distance from straight-line geodesic distance
   * using the South Indian urban road circuity factor (1.20 - 1.28).
   */
  calculateRoadDistance(straightLineKm: number): number {
    if (straightLineKm <= 0) return 0;
    const factor = straightLineKm <= 3.0 ? 1.20 : straightLineKm <= 10.0 ? 1.24 : 1.28;
    return Number((straightLineKm * factor).toFixed(1));
  }

  /**
   * Estimates urban delivery transit time in minutes (24 km/h average dispatch speed + 5 min buffer).
   */
  calculateDurationMinutes(roadDistKm: number): number {
    if (roadDistKm <= 0) return 5;
    return Math.round(5 + (roadDistKm / 24.0) * 60.0);
  }

  /**
   * High-precision geodesic distance calculation (WGS-84 Vincenty inverse formula with Haversine fallback)
   */
  private haversineDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
    if (lat1 === lat2 && lon1 === lon2) return 0;
    const a = 6378137.0;
    const b = 6356752.314245;
    const f = 1 / 298.257223563;

    const L = ((lon2 - lon1) * Math.PI) / 180;
    const U1 = Math.atan((1 - f) * Math.tan((lat1 * Math.PI) / 180));
    const U2 = Math.atan((1 - f) * Math.tan((lat2 * Math.PI) / 180));
    const sinU1 = Math.sin(U1), cosU1 = Math.cos(U1);
    const sinU2 = Math.sin(U2), cosU2 = Math.cos(U2);

    let lambda = L;
    let lambdaP = 2 * Math.PI;
    let iterLimit = 20;
    let cosSqAlpha = 0, sinSigma = 0, cos2SigmaM = 0, cosSigma = 0, sigma = 0;

    while (Math.abs(lambda - lambdaP) > 1e-12 && --iterLimit > 0) {
      const sinLambda = Math.sin(lambda), cosLambda = Math.cos(lambda);
      sinSigma = Math.sqrt(
        (cosU2 * sinLambda) * (cosU2 * sinLambda) +
        (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda)
      );
      if (sinSigma === 0) return 0;
      cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
      sigma = Math.atan2(sinSigma, cosSigma);
      const sinAlpha = (cosU1 * cosU2 * sinLambda) / sinSigma;
      cosSqAlpha = 1 - sinAlpha * sinAlpha;
      cos2SigmaM = cosSqAlpha !== 0 ? cosSigma - (2 * sinU1 * sinU2) / cosSqAlpha : 0;
      const C = (f / 16) * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha));
      lambdaP = lambda;
      lambda = L + (1 - C) * f * sinAlpha * (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)));
    }

    if (iterLimit === 0) {
      const R = 6371;
      const dLat = ((lat2 - lat1) * Math.PI) / 180;
      const dLon = ((lon2 - lon1) * Math.PI) / 180;
      const aH = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
      return R * 2 * Math.atan2(Math.sqrt(aH), Math.sqrt(1 - aH));
    }

    const uSq = (cosSqAlpha * (a * a - b * b)) / (b * b);
    const A = 1 + (uSq / 16384) * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)));
    const B = (uSq / 1024) * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)));
    const deltaSigma = B * sinSigma * (cos2SigmaM + (B / 4) * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) - (B / 6) * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)));
    const s = b * A * (sigma - deltaSigma);
    return s / 1000;
  }
}
