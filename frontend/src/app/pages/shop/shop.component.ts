import { Component, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
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
  cartMessage: string = '';

  // True Page-Based Pagination
  currentPage: number = 0; // 0-indexed for backend API
  pageSize: number = 12;   // Responsive: 12 desktop, 9 tablet, 6 mobile
  totalPages: number = 0;
  totalElements: number = 0;

  // Exclusive Accordion Filter State: 'category' expanded by default, others collapsed
  expandedSection: 'category' | 'brand' | 'petType' | 'availability' | null = 'category';
  isMobileFilterOpen: boolean = false;

  // Department Hub & Need Filters
  activeDepartment: 'all' | 'dog-food' | 'dog-care' | 'cat-food' | 'cat-care' = 'all';
  selectedNeed: string = 'All';

  // Filters State
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
    private router: Router,
    private http: HttpClient,
    private cartService: CartService
  ) {}

  ngOnInit(): void {
    this.updatePageSizeForViewport();
    this.loadCatalogFilters();
    this.route.queryParams.subscribe(params => {
      // 1. Text Query
      this.searchQuery = params['query'] || '';

      // 2. Category mapping (handles direct navbar routes & query params)
      const rawCat = params['category'];
      if (!rawCat || rawCat === 'all') {
        this.selectedCategory = 'All';
      } else if (rawCat === 'cat-food' || rawCat === 'cats') {
        this.selectedCategory = 'Cat Food';
        this.expandedSection = 'category';
      } else if (rawCat === 'dog-food' || rawCat === 'dogs') {
        this.selectedCategory = 'Dog Food';
        this.expandedSection = 'category';
      } else if (rawCat === 'grooming' || rawCat === 'grooming-hygiene') {
        this.selectedCategory = 'Grooming';
        this.expandedSection = 'category';
      } else if (rawCat === 'hygiene') {
        this.selectedCategory = 'Hygiene';
        this.expandedSection = 'category';
      } else if (rawCat === 'accessories') {
        this.selectedCategory = 'Accessories';
        this.expandedSection = 'category';
      } else if (rawCat === 'supplements') {
        this.selectedCategory = 'Supplements';
        this.expandedSection = 'category';
      } else {
        this.selectedCategory = rawCat;
      }

      // 3. Brand mapping
      const rawBrand = params['brand'];
      if (!rawBrand || rawBrand === 'all') {
        this.selectedBrand = 'All';
      } else if (rawBrand.toLowerCase() === 'farmina-nd' || rawBrand.toLowerCase() === 'farmina') {
        this.selectedBrand = 'Farmina N&D';
      } else if (rawBrand.toLowerCase() === 'royal-canin') {
        this.selectedBrand = 'Royal Canin';
      } else if (rawBrand.toLowerCase() === 'sheba') {
        this.selectedBrand = 'Sheba';
      } else if (rawBrand.toLowerCase() === 'pedigree') {
        this.selectedBrand = 'Pedigree';
      } else if (rawBrand.toLowerCase() === 'whiskas') {
        this.selectedBrand = 'Whiskas';
      } else if (rawBrand.toLowerCase() === 'drools') {
        this.selectedBrand = 'Drools';
      } else if (rawBrand.toLowerCase() === 'himalaya') {
        this.selectedBrand = 'Himalaya';
      } else {
        this.selectedBrand = rawBrand;
      }

      // 4. Pet Type & Stock
      this.selectedPetType = params['petType'] || 'All';
      this.inStockOnly = params['inStock'] === 'true';
      if (params['sort']) {
        this.sortBy = params['sort'];
      }

      this.currentPage = 0;
      this.loadProducts();
    });
  }

  @HostListener('window:resize')
  onResize(): void {
    const prevSize = this.pageSize;
    this.updatePageSizeForViewport();
    if (prevSize !== this.pageSize) {
      this.currentPage = 0;
      this.loadProducts();
    }
  }

  private updatePageSizeForViewport(): void {
    if (typeof window !== 'undefined') {
      const width = window.innerWidth;
      if (width < 768) {
        this.pageSize = 6;
      } else if (width < 1024) {
        this.pageSize = 9;
      } else {
        this.pageSize = 12;
      }
    }
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

  loadProducts(): void {
    this.isLoading = true;

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

    this.http.get<{ success: boolean; data: { content: any[]; totalElements: number; totalPages: number; last: boolean } }>(url).subscribe({
      next: (res) => {
        const items = res?.data?.content || [];
        this.products = items.map(p => this.mapToCard(p));
        this.totalElements = res?.data?.totalElements ?? this.products.length;
        this.totalPages = res?.data?.totalPages ?? Math.ceil(this.totalElements / this.pageSize);
        this.isLoading = false;
      },
      error: () => {
        // Robust Client-Side Fallback Filtering when backend is offline
        this.applyFallbackFilter();
      }
    });
  }

  // Filter & Search Triggers (Synchronizes with URL and resets page to 0)
  onFilterChange(): void {
    this.currentPage = 0;
    const queryParams: any = {};

    if (this.searchQuery.trim()) {
      queryParams['query'] = this.searchQuery.trim();
    } else {
      queryParams['query'] = null;
    }

    if (this.selectedCategory !== 'All') {
      queryParams['category'] = this.selectedCategory.toLowerCase().replace(/[\s&]+/g, '-');
    } else {
      queryParams['category'] = null;
    }

    if (this.selectedBrand !== 'All') {
      queryParams['brand'] = this.selectedBrand.toLowerCase().replace(/[\s&]+/g, '-');
    } else {
      queryParams['brand'] = null;
    }

    if (this.selectedPetType !== 'All') {
      queryParams['petType'] = this.selectedPetType;
    } else {
      queryParams['petType'] = null;
    }

    if (this.inStockOnly) {
      queryParams['inStock'] = 'true';
    } else {
      queryParams['inStock'] = null;
    }

    if (this.sortBy !== 'id,desc') {
      queryParams['sort'] = this.sortBy;
    } else {
      queryParams['sort'] = null;
    }

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge'
    });

    this.loadProducts();
  }

  // Exclusive Accordion Toggle
  toggleSection(section: 'category' | 'brand' | 'petType' | 'availability'): void {
    this.expandedSection = this.expandedSection === section ? null : section;
  }

  // Mobile Filter Drawer Controls
  toggleMobileFilter(): void {
    this.isMobileFilterOpen = !this.isMobileFilterOpen;
  }

  closeMobileFilter(): void {
    this.isMobileFilterOpen = false;
  }

  // Page-based Pagination Controls
  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages && page !== this.currentPage) {
      this.currentPage = page;
      this.loadProducts();
      if (typeof window !== 'undefined') {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
    }
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.goToPage(this.currentPage + 1);
    }
  }

  prevPage(): void {
    if (this.currentPage > 0) {
      this.goToPage(this.currentPage - 1);
    }
  }

  get pageNumbers(): number[] {
    const pages: number[] = [];
    const maxVisible = 5;
    let start = Math.max(0, this.currentPage - Math.floor(maxVisible / 2));
    let end = Math.min(this.totalPages - 1, start + maxVisible - 1);

    if (end - start + 1 < maxVisible) {
      start = Math.max(0, end - maxVisible + 1);
    }

    for (let i = start; i <= end; i++) {
      pages.push(i);
    }
    return pages;
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

  selectDepartment(dept: 'all' | 'dog-food' | 'dog-care' | 'cat-food' | 'cat-care'): void {
    this.activeDepartment = dept;
    this.currentPage = 0;
    this.selectedNeed = 'All';

    if (dept === 'all') {
      this.selectedCategory = 'All';
      this.selectedPetType = 'All';
    } else if (dept === 'dog-food') {
      this.selectedCategory = 'Dog Food';
      this.selectedPetType = 'Dog';
    } else if (dept === 'dog-care') {
      this.selectedCategory = 'Grooming';
      this.selectedPetType = 'Dog';
    } else if (dept === 'cat-food') {
      this.selectedCategory = 'Cat Food';
      this.selectedPetType = 'Cat';
    } else if (dept === 'cat-care') {
      this.selectedCategory = 'Hygiene';
      this.selectedPetType = 'Cat';
    }

    this.onFilterChange();
  }

  selectNeed(need: string): void {
    this.selectedNeed = need;
    this.currentPage = 0;
    if (need === 'All') {
      this.searchQuery = '';
    } else {
      this.searchQuery = need;
    }
    this.onFilterChange();
  }

  resetFilters(): void {
    this.activeDepartment = 'all';
    this.selectedNeed = 'All';
    this.searchQuery = '';
    this.selectedPetType = 'All';
    this.selectedCategory = 'All';
    this.selectedBrand = 'All';
    this.inStockOnly = false;
    this.sortBy = 'id,desc';
    this.currentPage = 0;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {}
    });
    this.loadProducts();
  }

  private applyFallbackFilter(): void {
    let items = this.getFallbackCatalog();

    // 1. Search Query Filter
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.trim().toLowerCase();
      items = items.filter(p =>
        p.name.toLowerCase().includes(q) ||
        p.brand.toLowerCase().includes(q) ||
        p.category.toLowerCase().includes(q)
      );
    }

    // 2. Category Filter
    if (this.selectedCategory !== 'All') {
      const cat = this.selectedCategory.toLowerCase();
      items = items.filter(p => {
        const pCat = p.category.toLowerCase();
        if (cat === 'cat food') {
          return pCat.includes('cat food') || p.petType.toLowerCase() === 'cat';
        } else if (cat === 'dog food') {
          return pCat.includes('dog food') || p.petType.toLowerCase() === 'dog';
        } else if (cat === 'grooming') {
          return pCat.includes('grooming') || pCat.includes('shampoo') || p.name.toLowerCase().includes('shampoo') || p.name.toLowerCase().includes('comb');
        } else if (cat === 'hygiene') {
          return pCat.includes('hygiene') || pCat.includes('litter') || pCat.includes('pad') || p.name.toLowerCase().includes('litter') || p.name.toLowerCase().includes('pads');
        } else if (cat === 'accessories') {
          return pCat.includes('accessories') || pCat.includes('toy') || p.name.toLowerCase().includes('toy') || p.name.toLowerCase().includes('harness');
        } else if (cat === 'supplements') {
          return pCat.includes('supplement') || pCat.includes('vitamin');
        }
        return pCat.includes(cat);
      });
    }

    // 3. Brand Filter
    if (this.selectedBrand !== 'All') {
      const b = this.selectedBrand.toLowerCase();
      items = items.filter(p => p.brand.toLowerCase() === b || p.brand.toLowerCase().includes(b));
    }

    // 4. Pet Type Filter
    if (this.selectedPetType !== 'All') {
      const pt = this.selectedPetType.toLowerCase();
      items = items.filter(p =>
        p.petType.toLowerCase() === pt || p.petType.toLowerCase() === 'all pets'
      );
    }

    // 5. In-Stock Filter
    if (this.inStockOnly) {
      items = items.filter(p => p.inStock);
    }

    // 6. Sort
    if (this.sortBy === 'basePrice,asc') {
      items.sort((a, b) => (a.discountedPrice ?? a.price) - (b.discountedPrice ?? b.price));
    } else if (this.sortBy === 'basePrice,desc') {
      items.sort((a, b) => (b.discountedPrice ?? b.price) - (a.discountedPrice ?? a.price));
    } else {
      items.sort((a, b) => b.id - a.id);
    }

    this.totalElements = items.length;
    this.totalPages = Math.ceil(this.totalElements / this.pageSize);

    // 7. Paginate
    const start = this.currentPage * this.pageSize;
    this.products = items.slice(start, start + this.pageSize);
    this.isLoading = false;
  }

  private getFallbackCatalog(): ProductCardData[] {
    return [
      {
        id: 101,
        name: 'Royal Canin Maxi Adult Dry Dog Food (4 kg)',
        brand: 'Royal Canin',
        category: 'Dog Food',
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
        category: 'Cat Food',
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
        category: 'Dog Food',
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
        category: 'Grooming',
        petType: 'All Pets',
        price: 260,
        discountedPrice: 235,
        imageUrl: 'https://images.unsplash.com/photo-1535294435445-d7249524ef2e?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 30,
        rating: 4.6,
        reviewCount: 41,
        returnAllowed: true
      },
      {
        id: 105,
        name: 'Royal Canin Kitten Gravy Wet Cat Food (85 g Pouch)',
        brand: 'Royal Canin',
        category: 'Cat Food',
        petType: 'Cat',
        price: 110,
        discountedPrice: 99,
        imageUrl: 'https://images.unsplash.com/photo-1574158622682-e40e69881006?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 40,
        rating: 4.9,
        reviewCount: 65,
        isFeatured: true,
        returnAllowed: true
      },
      {
        id: 106,
        name: 'Pedigree Adult Complete Nutrition Chicken & Veg (3 kg)',
        brand: 'Pedigree',
        category: 'Dog Food',
        petType: 'Dog',
        price: 850,
        discountedPrice: 780,
        imageUrl: 'https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 20,
        rating: 4.7,
        reviewCount: 44,
        returnAllowed: true
      },
      {
        id: 107,
        name: 'Sheba Succulent Chicken Breast Cat Wet Pate (70 g)',
        brand: 'Sheba',
        category: 'Cat Food',
        petType: 'Cat',
        price: 95,
        discountedPrice: 85,
        imageUrl: 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 35,
        rating: 4.8,
        reviewCount: 29,
        returnAllowed: true
      },
      {
        id: 108,
        name: 'Drools Focus Puppy Super Premium Dog Food (4 kg)',
        brand: 'Drools',
        category: 'Dog Food',
        petType: 'Dog',
        price: 1900,
        discountedPrice: 1699,
        imageUrl: 'https://images.unsplash.com/photo-1591769225440-811ad7d6eab2?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 12,
        rating: 4.6,
        reviewCount: 22,
        returnAllowed: true
      },
      {
        id: 109,
        name: 'Farmina N&D Pumpkin Lamb & Blueberry Adult Dog (2.5 kg)',
        brand: 'Farmina N&D',
        category: 'Dog Food',
        petType: 'Dog',
        price: 2490,
        discountedPrice: 2250,
        imageUrl: 'https://images.unsplash.com/photo-1601758228041-f3b2795255f1?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 15,
        rating: 4.9,
        reviewCount: 31,
        isFeatured: true,
        returnAllowed: true
      },
      {
        id: 110,
        name: 'Farmina N&D Prime Cat Chicken & Pomegranate (1.5 kg)',
        brand: 'Farmina N&D',
        category: 'Cat Food',
        petType: 'Cat',
        price: 2150,
        discountedPrice: 1950,
        imageUrl: 'https://images.unsplash.com/photo-1535930891776-0c2dfb7fda1a?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 18,
        rating: 4.9,
        reviewCount: 28,
        isFeatured: true,
        returnAllowed: true
      },
      {
        id: 111,
        name: 'Petzlife Professional Stainless Steel De-Shedding Comb',
        brand: 'Nova Petzlife',
        category: 'Grooming',
        petType: 'All Pets',
        price: 450,
        discountedPrice: 399,
        imageUrl: 'https://images.unsplash.com/photo-1516734212186-a967f81ad0d7?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 22,
        rating: 4.7,
        reviewCount: 16,
        returnAllowed: true
      },
      {
        id: 112,
        name: 'Himalaya Fresh Coat Herbal Pet Grooming Spray (150 ml)',
        brand: 'Himalaya',
        category: 'Grooming',
        petType: 'All Pets',
        price: 320,
        discountedPrice: 285,
        imageUrl: 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 28,
        rating: 4.5,
        reviewCount: 25,
        returnAllowed: true
      },
      {
        id: 113,
        name: 'Cat Best Clumping Natural Wood Cat Litter (10 L)',
        brand: 'Nova Petzlife',
        category: 'Hygiene',
        petType: 'Cat',
        price: 1150,
        discountedPrice: 1050,
        imageUrl: 'https://images.unsplash.com/photo-1563460716037-460b3dd145dc?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 16,
        rating: 4.8,
        reviewCount: 47,
        returnAllowed: true
      },
      {
        id: 114,
        name: 'Simple Solution Extreme Stain & Odor Eliminator Spray (750 ml)',
        brand: 'Nova Petzlife',
        category: 'Hygiene',
        petType: 'All Pets',
        price: 890,
        discountedPrice: 820,
        imageUrl: 'https://images.unsplash.com/photo-1526336024174-e58f5cdd8e13?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 14,
        rating: 4.7,
        reviewCount: 33,
        returnAllowed: true
      },
      {
        id: 115,
        name: 'Nova Petzlife Charcoal Puppy Training Pee Pads (50 Pack)',
        brand: 'Nova Petzlife',
        category: 'Hygiene',
        petType: 'Dog',
        price: 799,
        discountedPrice: 699,
        imageUrl: 'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 20,
        rating: 4.8,
        reviewCount: 50,
        returnAllowed: true
      },
      {
        id: 116,
        name: 'Kong Classic Durable Natural Rubber Chew Dog Toy',
        brand: 'Nova Petzlife',
        category: 'Accessories',
        petType: 'Dog',
        price: 1250,
        discountedPrice: 1100,
        imageUrl: 'https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=600&auto=format&fit=crop&q=80',
        inStock: true,
        stockQuantity: 15,
        rating: 4.9,
        reviewCount: 62,
        returnAllowed: true
      }
    ];
  }
}
