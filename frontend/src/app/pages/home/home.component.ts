import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ProductCardComponent } from '../../components/product-card/product-card.component';
import { GoogleReviewsComponent } from '../../components/google-reviews/google-reviews.component';
import { ProductCardData, CategoryItem, ProductVariant } from '../../models/product.model';
import { CartService } from '../../core/services/cart.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, ProductCardComponent, GoogleReviewsComponent],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {
  constructor(
    private cartService: CartService,
    private http: HttpClient
  ) {}
  // Confirmed Shop Location Coordinates
  readonly shopCoordinates = {
    name: 'Petzlife (NOVA PETZLIFE)',
    lat: 8.528448,
    lng: 76.953268,
    address: 'Petzlife, Kuravankonam Junction, Kuravankonam Ambalamukku Road, Thiruvananthapuram, Kerala 695003',
    maxRadiusKm: 20
  };

  // Sample Categories
  categories: CategoryItem[] = [
    {
      id: 1,
      name: 'Cat Food & Nutrition',
      slug: 'cat-food',
      icon: '🐱',
      description: 'Dry kibble, wet gravy pouches, kitten & adult formulas',
      itemCount: 42,
      popularBrands: ['Royal Canin', 'Whiskas', 'Sheba']
    },
    {
      id: 2,
      name: 'Dog Food & Treats',
      slug: 'dog-food',
      icon: '🐶',
      description: 'Puppy starter, adult dog kibble, calcium chews & biscuits',
      itemCount: 56,
      popularBrands: ['Royal Canin', 'Pedigree', 'Drools']
    },
    {
      id: 3,
      name: 'Grooming & Shampoos',
      slug: 'grooming',
      icon: '✨',
      description: 'Anti-tick shampoos, coat conditioners, nail clippers & brushes',
      itemCount: 28,
      popularBrands: ['Himalaya', 'Beaphar', 'Bioline']
    },
    {
      id: 4,
      name: 'Litter & Pet Hygiene',
      slug: 'hygiene',
      icon: '🧼',
      description: 'Bentonite clumping cat litter, pee pads, deodorizing sprays',
      itemCount: 19,
      popularBrands: ['Intersand', 'Sanicat', 'Emily Pets']
    },
    {
      id: 5,
      name: 'Pet Toys & Accessories',
      slug: 'accessories',
      icon: '🎾',
      description: 'Chew toys, teaser wands, collars, harnesses, sturdy leashes',
      itemCount: 35,
      popularBrands: ['Kong', 'Petsport', 'Trixie']
    },
    {
      id: 6,
      name: 'Pet Health Supplements',
      slug: 'supplements',
      icon: '💊',
      description: 'Omega oils, joint care calcium, multivitamins & skin tonics',
      itemCount: 22,
      popularBrands: ['Virbac', 'Vetoquinol', 'Petup']
    }
  ];

  // Verified Brands with slugs for direct shop filtering
  featuredBrands = [
    { name: 'Royal Canin', slug: 'royal-canin', tag: 'Breed Health Nutrition' },
    { name: 'Whiskas', slug: 'whiskas', tag: 'Complete Cat Care' },
    { name: 'Pedigree', slug: 'pedigree', tag: 'Nutritious Dog Food' },
    { name: 'Drools', slug: 'drools', tag: 'Real Chicken & Egg' },
    { name: 'Sheba', slug: 'sheba', tag: 'Gourmet Wet Cat Food' },
    { name: 'Himalaya', slug: 'himalaya', tag: 'Herbal Pet Care' },
    { name: 'Farmina N&D', slug: 'farmina-nd', tag: 'Grain-Free Nutrition' }
  ];

  // Featured Products (Dynamic from API with fallback)
  featuredProducts: ProductCardData[] = [];

  // Delivery Radius Interactive Checker
  customerPincodeOrArea: string = '';
  isCheckingDelivery: boolean = false;
  deliveryEligibilityResult: {
    checked: boolean;
    isEligible: boolean;
    distanceKm?: number;
    deliveryCharge?: number;
    freeDeliveryThreshold?: number;
    message: string;
  } | null = null;

  // Known sample areas in Thiruvananthapuram with coordinates
  knownTvmLocations: { [key: string]: { lat: number; lng: number; name: string } } = {
    'kuravankonam': { lat: 8.528448, lng: 76.953268, name: 'Kuravankonam Junction' },
    'kowdiar': { lat: 8.524, lng: 76.965, name: 'Kowdiar' },
    'ambalamukku': { lat: 8.533, lng: 76.959, name: 'Ambalamukku' },
    'pattom': { lat: 8.526, lng: 76.942, name: 'Pattom' },
    'peroorkada': { lat: 8.539, lng: 76.974, name: 'Peroorkada' },
    'kesavadasapuram': { lat: 8.535, lng: 76.938, name: 'Kesavadasapuram' },
    'sasthamangalam': { lat: 8.512, lng: 76.969, name: 'Sasthamangalam' },
    'medical college': { lat: 8.523, lng: 76.928, name: 'Medical College' },
    'palayam': { lat: 8.503, lng: 76.951, name: 'Palayam' },
    'thampanoor': { lat: 8.488, lng: 76.953, name: 'Thampanoor' },
    'vazhuthacaud': { lat: 8.500, lng: 76.965, name: 'Vazhuthacaud' },
    'shangumugham': { lat: 8.479, lng: 76.911, name: 'Shangumugham' },
    'kazhakoottam': { lat: 8.568, lng: 76.871, name: 'Kazhakoottam' },
    'technopark': { lat: 8.558, lng: 76.881, name: 'Technopark' },
    'kovalam': { lat: 8.398, lng: 76.979, name: 'Kovalam' },
    'nedumangad': { lat: 8.601, lng: 77.001, name: 'Nedumangad' },
    'neyyattinkara': { lat: 8.401, lng: 77.086, name: 'Neyyattinkara' },
    'attingal': { lat: 8.696, lng: 76.814, name: 'Attingal' },
    '695003': { lat: 8.5284, lng: 76.9532, name: 'Kuravankonam / Kowdiar (695003)' },
    '695004': { lat: 8.526, lng: 76.942, name: 'Pattom (695004)' },
    '695001': { lat: 8.503, lng: 76.951, name: 'Palayam (695001)' },
    '695014': { lat: 8.500, lng: 76.965, name: 'Vazhuthacaud (695014)' },
    '695581': { lat: 8.558, lng: 76.881, name: 'Technopark (695581)' },
    '695121': { lat: 8.401, lng: 77.086, name: 'Neyyattinkara (695121)' }
  };

  ngOnInit(): void {
    this.loadFeaturedProducts();
  }

  loadFeaturedProducts(): void {
    this.http.get<any>(`${environment.apiUrl}/products/featured`).subscribe({
      next: (res) => {
        if (res?.success && Array.isArray(res.data) && res.data.length > 0) {
          this.featuredProducts = res.data.map((p: any) => this.mapToCard(p));
        } else {
          this.fetchInitialProducts();
        }
      },
      error: () => {
        this.fetchInitialProducts();
      }
    });
  }

  private fetchInitialProducts(): void {
    this.http.get<any>(`${environment.apiUrl}/products?page=0&size=6`).subscribe({
      next: (res) => {
        const items = res?.data?.content || [];
        if (items.length > 0) {
          this.featuredProducts = items.map((p: any) => this.mapToCard(p));
        } else {
          this.featuredProducts = this.getFallbackFeaturedProducts();
        }
      },
      error: () => {
        this.featuredProducts = this.getFallbackFeaturedProducts();
      }
    });
  }

  private mapToCard(p: any): ProductCardData {
    return {
      id: p.id,
      name: p.name,
      brand: p.brandName || 'Nova Petzlife',
      category: p.categoryName || 'Pet Supplies',
      petType: (p.petType === 'Cat' || p.petType === 'Dog') ? p.petType : 'All Pets',
      price: p.basePrice || 0,
      discountedPrice: p.discountedPrice || p.basePrice,
      imageUrl: p.primaryImageUrl || 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?auto=format&fit=crop&w=500&q=80',
      inStock: p.inStock !== false,
      stockQuantity: 10,
      rating: 4.8,
      reviewCount: 24,
      isFeatured: p.isFeatured,
      returnAllowed: p.returnAllowed !== false,
      variants: p.variants?.map((v: any) => ({
        id: v.id,
        name: v.variantName,
        price: v.price,
        discountedPrice: v.discountedPrice,
        stock: 10
      }))
    };
  }

  selectLocality(areaKey: string): void {
    this.customerPincodeOrArea = areaKey;
    this.checkDeliveryArea();
  }

  useCurrentLocation(): void {
    if (!navigator.geolocation) {
      alert('Geolocation is not supported by your browser.');
      return;
    }
    this.isCheckingDelivery = true;
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        this.customerPincodeOrArea = `Current GPS (${pos.coords.latitude.toFixed(4)}, ${pos.coords.longitude.toFixed(4)})`;
        this.verifyDeliveryCoordinates(pos.coords.latitude, pos.coords.longitude);
      },
      (err) => {
        this.isCheckingDelivery = false;
        alert('Could not determine your GPS location. Please type your locality or pincode.');
      },
      { timeout: 10000 }
    );
  }

  checkDeliveryArea(): void {
    const input = this.customerPincodeOrArea.trim().toLowerCase();
    if (!input) return;

    let matched: { lat: number; lng: number; name: string } | null = null;
    for (const [key, loc] of Object.entries(this.knownTvmLocations)) {
      if (input.includes(key) || key.includes(input)) {
        matched = loc;
        break;
      }
    }

    if (matched) {
      this.verifyDeliveryCoordinates(matched.lat, matched.lng);
    } else {
      // Default to city center coordinates for Thiruvananthapuram
      this.verifyDeliveryCoordinates(8.503, 76.951, input);
    }
  }

  private verifyDeliveryCoordinates(lat: number, lng: number, postalCode?: string): void {
    this.isCheckingDelivery = true;
    const body = {
      latitude: lat,
      longitude: lng,
      postalCode: postalCode || null,
      orderSubtotal: 600
    };

    this.http.post<any>(`${environment.apiUrl}/delivery/check-eligibility`, body).subscribe({
      next: (res) => {
        this.isCheckingDelivery = false;
        if (res?.success && res.data) {
          const d = res.data;
          this.deliveryEligibilityResult = {
            checked: true,
            isEligible: d.isEligible,
            distanceKm: d.distanceKm,
            deliveryCharge: d.deliveryCharge,
            freeDeliveryThreshold: d.freeDeliveryThreshold,
            message: d.message || (d.isEligible
              ? `Great news! Your location is ~${Number(d.distanceKm).toFixed(1)} km from our Kuravankonam store, within our 20 km delivery radius.`
              : `Your location is ~${Number(d.distanceKm).toFixed(1)} km from our Kuravankonam store, exceeding our 20 km delivery limit.`)
          };
        }
      },
      error: () => {
        // Fallback local Haversine computation if backend service is offline
        this.isCheckingDelivery = false;
        const dist = this.haversineDistance(
          this.shopCoordinates.lat,
          this.shopCoordinates.lng,
          lat,
          lng
        );
        const isEligible = dist <= this.shopCoordinates.maxRadiusKm;
        this.deliveryEligibilityResult = {
          checked: true,
          isEligible,
          distanceKm: dist,
          message: isEligible
            ? `Your location is ~${dist.toFixed(1)} km from Kuravankonam Junction, within our 20 km delivery zone.`
            : `Your location is ~${dist.toFixed(1)} km from Kuravankonam Junction, which exceeds our 20 km delivery radius.`
        };
      }
    });
  }

  private haversineDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
    const R = 6371; // Earth's radius in km
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
      Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  handleAddToCart(event: { product: ProductCardData; variant?: ProductVariant }): void {
    this.cartService.addItem(event.product.id, event.variant?.id, 1).subscribe({
      next: () => console.log('Successfully added to cart:', event.product.name),
      error: err => console.warn('Could not add to cart via API, item retained in session', err)
    });
  }

  private getFallbackFeaturedProducts(): ProductCardData[] {
    return [
      {
        id: 1,
        name: 'Royal Canin Kitten Dry Cat Food',
        brand: 'Royal Canin',
        category: 'Cat Food',
        subcategory: 'Kitten Food',
        petType: 'Cat',
        price: 990,
        discountedPrice: 890,
        imageUrl: 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?w=500&auto=format&fit=crop&q=60',
        inStock: true,
        stockQuantity: 18,
        rating: 4.9,
        reviewCount: 38,
        isFeatured: true,
        returnAllowed: true
      },
      {
        id: 6,
        name: 'Royal Canin Maxi Adult Dry Dog Food (4 kg)',
        brand: 'Royal Canin',
        category: 'Dog Food',
        subcategory: 'Adult Kibble',
        petType: 'Dog',
        price: 2950,
        discountedPrice: 2750,
        imageUrl: 'https://images.unsplash.com/photo-1568640347023-a616a30bc3bd?w=500&auto=format&fit=crop&q=60',
        inStock: true,
        stockQuantity: 12,
        rating: 4.9,
        reviewCount: 46,
        isFeatured: true,
        returnAllowed: true
      },
      {
        id: 46,
        name: 'Sheba Premium Tuna Fillet in Gravy (85g)',
        brand: 'Sheba',
        category: 'Cat Food',
        subcategory: 'Wet Food',
        petType: 'Cat',
        price: 85,
        discountedPrice: 80,
        imageUrl: 'https://images.unsplash.com/photo-1548767797-d8c844163c4c?w=500&auto=format&fit=crop&q=60',
        inStock: true,
        stockQuantity: 40,
        rating: 4.9,
        reviewCount: 19,
        isFeatured: true,
        returnAllowed: true
      }
    ];
  }
}
