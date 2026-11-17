import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ProductCardComponent } from '../../components/product-card/product-card.component';
import { ProductCardData, ProductVariant } from '../../models/product.model';
import { CartService } from '../../core/services/cart.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-shop',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, ProductCardComponent],
  templateUrl: './shop.component.html',
  styleUrls: ['./shop.component.scss']
})
export class ShopComponent implements OnInit {
  products: ProductCardData[] = [];
  isLoading: boolean = true;
  isLoadingMore: boolean = false;
  cartMessage: string = '';

  // Pagination
  currentPage: number = 0;
  pageSize: number = 12;
  totalElements: number = 0;
  hasMore: boolean = false;

  // Filters
  searchQuery: string = '';
  selectedPetType: string = 'All';
  selectedCategory: string = 'All';
  selectedBrand: string = 'All';
  inStockOnly: boolean = false;
  sortBy: string = 'id,desc';

  petTypes = ['All', 'Cat', 'Dog', 'Bird', 'Fish'];
  categories = ['All', 'Dog Food', 'Cat Food', 'Grooming', 'Hygiene', 'Accessories', 'Supplements'];
  brands = ['All', 'Royal Canin', 'Pedigree', 'Whiskas', 'Drools', 'Sheba', 'Himalaya', 'Farmina N&D'];

  constructor(
    private route: ActivatedRoute,
    private http: HttpClient,
    private cartService: CartService
  ) {}

  ngOnInit(): void {
    this.loadCatalogFilters();
    this.route.queryParams.subscribe(params => {
      if (params['query'] !== undefined) this.searchQuery = params['query'];
      if (params['category'] !== undefined) {
        const rawCat = params['category'];
        if (rawCat === 'cat-food') this.selectedCategory = 'Cat Food';
        else if (rawCat === 'dog-food') this.selectedCategory = 'Dog Food';
        else if (rawCat === 'grooming') this.selectedCategory = 'Grooming';
        else if (rawCat === 'hygiene') this.selectedCategory = 'Hygiene';
        else this.selectedCategory = rawCat;
      }
      if (params['brand'] !== undefined) {
        const rawBrand = params['brand'];
        if (rawBrand.toLowerCase() === 'farmina-nd' || rawBrand.toLowerCase() === 'farmina') {
          this.selectedBrand = 'Farmina N&D';
        } else if (rawBrand.toLowerCase() === 'royal-canin') {
          this.selectedBrand = 'Royal Canin';
        } else if (rawBrand.toLowerCase() === 'sheba') {
          this.selectedBrand = 'Sheba';
        } else {
          this.selectedBrand = rawBrand;
        }
      }
      if (params['petType'] !== undefined) this.selectedPetType = params['petType'];
      this.currentPage = 0;
      this.loadProducts(false);
    });
  }

  loadCatalogFilters(): void {
    // Dynamic Categories
    this.http.get<any>(`${environment.apiUrl}/categories/flat`).subscribe({
      next: res => {
        if (res?.success && res.data) {
          const names = res.data.map((c: any) => c.name);
          const uniqueNames = Array.from(new Set(['All', 'Dog Food', 'Cat Food', 'Grooming', 'Hygiene', ...names]));
          this.categories = uniqueNames;
        }
      }
    });

    // Dynamic Brands
    this.http.get<any>(`${environment.apiUrl}/brands`).subscribe({
      next: res => {
        if (res?.success && res.data) {
          const names = res.data.map((b: any) => b.name);
          const uniqueBrands = Array.from(new Set(['All', 'Royal Canin', 'Pedigree', 'Whiskas', 'Drools', 'Sheba', 'Himalaya', 'Farmina N&D', ...names]));
          this.brands = uniqueBrands;
        }
      }
    });
  }

  loadProducts(append: boolean = false): void {
    if (append) {
      this.isLoadingMore = true;
    } else {
      this.isLoading = true;
      this.currentPage = 0;
    }

    let url = `${environment.apiUrl}/products?page=${this.currentPage}&size=${this.pageSize}&sort=${this.sortBy}`;

    if (this.searchQuery.trim()) {
      url += `&query=${encodeURIComponent(this.searchQuery.trim())}`;
    }
    if (this.selectedPetType !== 'All') {
      url += `&petType=${encodeURIComponent(this.selectedPetType)}`;
    }
    if (this.selectedCategory !== 'All') {
      const slug = this.selectedCategory.toLowerCase().replace(/[\s&]+/g, '-');
      url += `&categorySlug=${encodeURIComponent(slug)}`;
    }
    if (this.selectedBrand !== 'All') {
      const slug = this.selectedBrand.toLowerCase().replace(/[\s&]+/g, '-');
      url += `&brandSlug=${encodeURIComponent(slug)}`;
    }
    if (this.inStockOnly) {
      url += `&inStockOnly=true`;
    }

    this.http.get<{ success: boolean; data: { content: any[]; totalElements: number; last: boolean } }>(url).subscribe({
      next: (res) => {
        const items = res?.data?.content || [];
        const mapped = items.map(p => this.mapToCard(p));
        if (append) {
          this.products = [...this.products, ...mapped];
        } else {
          this.products = mapped;
        }
        this.totalElements = res?.data?.totalElements ?? this.products.length;
        this.hasMore = this.products.length < this.totalElements;
        this.isLoading = false;
        this.isLoadingMore = false;
      },
      error: () => {
        // Fallback demo catalog if backend is not seeded
        const fallback = this.getFallbackCatalog();
        if (append) {
          this.products = [...this.products, ...fallback];
        } else {
          this.products = fallback;
        }
        this.totalElements = this.products.length;
        this.hasMore = false;
        this.isLoading = false;
        this.isLoadingMore = false;
      }
    });
  }

  loadMore(): void {
    if (this.isLoadingMore || !this.hasMore) return;
    this.currentPage++;
    this.loadProducts(true);
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
      reviewCount: 14,
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

  handleAddToCart(event: { product: ProductCardData; variant?: ProductVariant }): void {
    this.cartService.addItem(event.product.id, event.variant?.id, 1).subscribe();
    this.cartMessage = `Added "${event.product.name}" to cart!`;
    setTimeout(() => { this.cartMessage = ''; }, 3000);
  }

  resetFilters(): void {
    this.searchQuery = '';
    this.selectedPetType = 'All';
    this.selectedCategory = 'All';
    this.selectedBrand = 'All';
    this.inStockOnly = false;
    this.sortBy = 'id,desc';
    this.currentPage = 0;
    this.loadProducts(false);
  }

  private getFallbackCatalog(): ProductCardData[] {
    return [
      {
        id: 101,
        name: 'Royal Canin Maxi Adult Dry Dog Food (4 kg)',
        brand: 'Royal Canin',
        category: 'Dog Food & Nutrition',
        petType: 'Dog',
        price: 2950,
        discountedPrice: 2750,
        imageUrl: 'https://images.unsplash.com/photo-1589924691995-400dc9ecc119?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 14,
        rating: 4.9,
        reviewCount: 38,
        isFeatured: true,
        returnAllowed: true
      },
      {
        id: 102,
        name: 'Whiskas Ocean Fish Adult Wet Cat Food (Pack of 12)',
        brand: 'Whiskas',
        category: 'Cat Food & Nutrition',
        petType: 'Cat',
        price: 600,
        discountedPrice: 540,
        imageUrl: 'https://images.unsplash.com/photo-1548767797-d8c844163c4c?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 25,
        rating: 4.8,
        reviewCount: 52,
        isFeatured: true,
        returnAllowed: true
      },
      {
        id: 103,
        name: 'Arden Grange Adult Dog Fresh Chicken & Rice (2 kg)',
        brand: 'Arden Grange',
        category: 'Dog Food & Nutrition',
        petType: 'Dog',
        price: 1850,
        discountedPrice: 1720,
        imageUrl: 'https://images.unsplash.com/photo-1568640347023-a616a30bc3bd?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 8,
        rating: 4.7,
        reviewCount: 19,
        isFeatured: true,
        returnAllowed: true
      },
      {
        id: 104,
        name: 'Himalaya Erina-EP Anti-Tick Pet Shampoo (200 ml)',
        brand: 'Himalaya',
        category: 'Grooming & Hygiene',
        petType: 'All Pets',
        price: 260,
        discountedPrice: 235,
        imageUrl: 'https://images.unsplash.com/photo-1535294435445-d7249524ef2e?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 30,
        rating: 4.6,
        reviewCount: 41,
        returnAllowed: true
      }
    ];
  }
}
