import { Component, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ProductCardComponent } from '../../components/product-card/product-card.component';
import { ProductCardData, ProductVariant } from '../../models/product.model';
import { CartService } from '../../core/services/cart.service';
import { environment } from '../../../environments/environment';

export interface DepartmentItem {
  id: string;
  name: string;
  icon: string;
  subtitle: string;
  tags: string[];
  petType?: string;
  categorySlug?: string;
  isCustom?: boolean;
}

@Component({
  selector: 'app-shop',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, ProductCardComponent],
  templateUrl: './shop.component.html',
  styleUrls: ['./shop.component.scss']
})
export class ShopComponent implements OnInit {
  products: ProductCardData[] = [];
  similarProducts: ProductCardData[] = [];
  isLoading: boolean = false;
  isLoadingSimilar: boolean = false;
  cartMessage: string = '';

  // View state: true = inside a department view; false = 4 cards hub
  isDepartmentSelected: boolean = false;
  activeDepartmentId: string | null = null;
  activeDepartmentTitle: string = '';
  activeDepartmentSubtitle: string = '';
  activeDepartmentIcon: string = '🐾';

  // 6 Curated Core Department Cards
  departmentCards: DepartmentItem[] = [
    {
      id: 'dog-food',
      name: 'Dog Food',
      icon: '🥩',
      subtitle: 'Dry Kibble, Wet Gravy & Puppy Nutrition',
      tags: ['Dry Kibble', 'Wet Gravy', 'Puppy Nutrition'],
      petType: 'Dog',
      categorySlug: 'dog-food'
    },
    {
      id: 'dog-care',
      name: 'Dog Care',
      icon: '🐕',
      subtitle: 'Grooming, Flea & Tick, Collars & Supplements',
      tags: ['Anti-Tick', 'Herbal Shampoo', 'Collars & Toys'],
      petType: 'Dog',
      categorySlug: 'dog-care'
    },
    {
      id: 'cat-food',
      name: 'Cat Food',
      icon: '🐟',
      subtitle: 'Kitten & Adult Kibble, Gravy Pouches & Treats',
      tags: ['Kitten Formula', 'Ocean Fish', 'Gravy Pouches'],
      petType: 'Cat',
      categorySlug: 'cat-food'
    },
    {
      id: 'cat-care',
      name: 'Cat Care',
      icon: '🐈',
      subtitle: 'Clumping Litter, Deodorizers, Scratchers & Brushes',
      tags: ['Wood Litter', 'Deodorizer', 'Grooming Brushes'],
      petType: 'Cat',
      categorySlug: 'cat-care'
    },
    {
      id: 'grooming',
      name: 'Grooming & Hygiene',
      icon: '✨',
      subtitle: 'Shampoos, Conditioners, Brushes & Oral Care',
      tags: ['Herbal Shampoos', 'Shedding Brushes', 'Paw & Dental Care'],
      petType: 'All Pets',
      categorySlug: 'grooming'
    },
    {
      id: 'all',
      name: 'Browse All Products',
      icon: '📦',
      subtitle: 'Complete verified catalog across all categories & brands',
      tags: ['Full Catalog', 'All Categories', 'Every Pet Item'],
      petType: 'All Pets',
      categorySlug: 'all'
    }
  ];

  // Pagination State
  currentPage: number = 0;
  pageSize: number = 12;
  totalPages: number = 0;
  totalElements: number = 0;

  // Active Department Filter Controls
  searchQuery: string = '';
  selectedBrand: string = 'All';
  inStockOnly: boolean = false;
  sortBy: string = 'id,desc';

  // Available brands for the currently active department
  availableBrandsForDepartment: string[] = ['All'];

  // Global filters cache
  allCategories: string[] = ['All', 'Dog Food', 'Cat Food', 'Grooming', 'Hygiene', 'Accessories', 'Supplements'];
  allBrands: string[] = ['All', 'Royal Canin', 'Pedigree', 'Whiskas', 'Drools', 'Sheba', 'Himalaya', 'Farmina N&D'];

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
      const dept = params['department'];
      const cat = params['category'];
      const query = params['query'];
      const brand = params['brand'];
      const view = params['view'];
      const inStock = params['inStock'];
      const sort = params['sort'];

      // Check if user has navigated into a specific department, search, or view
      if (dept || cat || query || brand || view === 'all') {
        this.isDepartmentSelected = true;

        if (dept) {
          this.activeDepartmentId = dept;
        } else if (cat) {
          // Map category param to department id
          this.activeDepartmentId = this.mapCategoryToDepartment(cat);
        } else if (view === 'all' || query || brand) {
          this.activeDepartmentId = 'all';
        }

        this.searchQuery = query || '';
        this.selectedBrand = brand || 'All';
        this.inStockOnly = inStock === 'true';
        if (sort) {
          this.sortBy = sort;
        }

        this.updateDepartmentMetadata();
        this.currentPage = 0;
        this.loadProducts();
      } else {
        // Landing on All Products Hub (4 medium-sized cards)
        this.isDepartmentSelected = false;
        this.activeDepartmentId = null;
        this.searchQuery = '';
        this.selectedBrand = 'All';
        this.inStockOnly = false;
      }
    });
  }

  @HostListener('window:resize')
  onResize(): void {
    const prevSize = this.pageSize;
    this.updatePageSizeForViewport();
    if (prevSize !== this.pageSize && this.isDepartmentSelected) {
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

  /**
   * Load categories & brands from backend API for search and filters.
   */
  loadCatalogFilters(): void {
    // Dynamic Categories from Admin/Backend for filter controls
    this.http.get<any>(`${environment.apiUrl}/categories/flat`).subscribe({
      next: res => {
        if (res?.success && Array.isArray(res.data)) {
          const names = res.data.map((c: any) => c.name);
          this.allCategories = Array.from(new Set(['All', 'Dog Food', 'Cat Food', 'Grooming', 'Hygiene', ...names]));
        }
      },
      error: () => {
        // Fallback categories already initialized
      }
    });

    // Dynamic Brands
    this.http.get<any>(`${environment.apiUrl}/brands`).subscribe({
      next: res => {
        if (res?.success && Array.isArray(res.data)) {
          const names = res.data.map((b: any) => b.name);
          this.allBrands = Array.from(new Set(['All', 'Royal Canin', 'Pedigree', 'Whiskas', 'Drools', 'Sheba', 'Himalaya', 'Farmina N&D', ...names]));
        }
      }
    });
  }

  /**
   * User selects one of the 6 curated department cards.
   * Navigates to dedicated department view or full catalog.
   */
  selectDepartment(deptId: string): void {
    if (deptId === 'all') {
      this.viewAllCatalog();
    } else {
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { department: deptId },
        queryParamsHandling: ''
      });
    }
  }

  /**
   * Returns to the 4 cards Hub view.
   */
  backToDepartments(): void {
    this.isDepartmentSelected = false;
    this.activeDepartmentId = null;
    this.searchQuery = '';
    this.selectedBrand = 'All';
    this.inStockOnly = false;
    this.currentPage = 0;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {}
    });
  }

  /**
   * View full store catalog without department filter.
   */
  viewAllCatalog(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { view: 'all' }
    });
  }

  private mapCategoryToDepartment(cat: string): string {
    const lower = cat.toLowerCase();
    if (lower === 'dog-food' || lower === 'dogs' || lower.includes('dog food')) {
      return 'dog-food';
    } else if (lower === 'cat-food' || lower === 'cats' || lower.includes('cat food')) {
      return 'cat-food';
    } else if (lower.includes('grooming') || lower.includes('hygiene')) {
      return 'grooming';
    }
    return lower;
  }

  private updateDepartmentMetadata(): void {
    const card = this.departmentCards.find(d => d.id === this.activeDepartmentId);
    if (card) {
      this.activeDepartmentTitle = card.name;
      this.activeDepartmentIcon = card.icon;
      if (card.id === 'dog-food') {
        this.activeDepartmentSubtitle = 'Premium dry kibble, wet gravy pouches, puppy nutrition & treats in stock at our store.';
      } else if (card.id === 'dog-care') {
        this.activeDepartmentSubtitle = 'Shampoos, anti-tick care, grooming combs, hygiene pads, durable toys & health supplements.';
      } else if (card.id === 'cat-food') {
        this.activeDepartmentSubtitle = 'Kitten formulas, ocean fish wet pouches, adult crunchy kibble & delicious cat treats.';
      } else if (card.id === 'cat-care') {
        this.activeDepartmentSubtitle = 'Natural clumping wood litter, stain & odor neutralizers, scratchers & grooming brushes.';
      } else {
        this.activeDepartmentSubtitle = card.subtitle;
      }
    } else if (this.activeDepartmentId === 'all') {
      this.activeDepartmentTitle = 'Complete Store Catalog';
      this.activeDepartmentIcon = '🛍️';
      this.activeDepartmentSubtitle = 'Browsing all verified pet foods, grooming essentials & accessories in stock.';
    } else if (this.activeDepartmentId === 'grooming') {
      this.activeDepartmentTitle = 'Grooming & Hygiene';
      this.activeDepartmentIcon = '✨';
      this.activeDepartmentSubtitle = 'Herbal shampoos, tick treatments, de-shedding tools & sanitary pads.';
    } else {
      this.activeDepartmentTitle = this.activeDepartmentId || 'Department Catalog';
      this.activeDepartmentIcon = '🐾';
      this.activeDepartmentSubtitle = 'Verified products available for direct store dispatch in Thiruvananthapuram.';
    }
  }

  loadProducts(): void {
    this.isLoading = true;

    let url = `${environment.apiUrl}/products?page=${this.currentPage}&size=${this.pageSize}&sort=${this.sortBy}`;

    // Apply department constraints
    if (this.activeDepartmentId === 'dog-food') {
      url += `&petType=Dog&categorySlug=dog-food`;
    } else if (this.activeDepartmentId === 'cat-food') {
      url += `&petType=Cat&categorySlug=cat-food`;
    } else if (this.activeDepartmentId === 'dog-care') {
      url += `&petType=Dog`;
    } else if (this.activeDepartmentId === 'cat-care') {
      url += `&petType=Cat`;
    } else if (this.activeDepartmentId === 'grooming') {
      url += `&categorySlug=grooming`;
    }

    if (this.searchQuery.trim()) {
      url += `&query=${encodeURIComponent(this.searchQuery.trim())}`;
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
        if (items.length > 0) {
          this.products = items.map(p => this.mapToCard(p));
          this.totalElements = res?.data?.totalElements ?? this.products.length;
          this.totalPages = res?.data?.totalPages ?? Math.ceil(this.totalElements / this.pageSize);
          this.populateAvailableBrands(this.products);
          this.similarProducts = [];
          this.isLoading = false;
        } else {
          // If backend returns empty, apply high-precision client fallback
          this.applyFallbackFilter();
        }
      },
      error: () => {
        this.applyFallbackFilter();
      }
    });
  }

  onFilterChange(): void {
    this.currentPage = 0;
    const queryParams: any = {};

    if (this.activeDepartmentId) {
      queryParams['department'] = this.activeDepartmentId;
    }
    if (this.searchQuery.trim()) {
      queryParams['query'] = this.searchQuery.trim();
    }
    if (this.selectedBrand !== 'All') {
      queryParams['brand'] = this.selectedBrand;
    }
    if (this.inStockOnly) {
      queryParams['inStock'] = 'true';
    }
    if (this.sortBy !== 'id,desc') {
      queryParams['sort'] = this.sortBy;
    }

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams
    });

    this.loadProducts();
  }

  resetDepartmentFilters(): void {
    this.searchQuery = '';
    this.selectedBrand = 'All';
    this.inStockOnly = false;
    this.sortBy = 'id,desc';
    this.onFilterChange();
  }

  private populateAvailableBrands(items: ProductCardData[]): void {
    const brandsSet = new Set<string>();
    items.forEach(item => {
      if (item.brand) {
        brandsSet.add(item.brand);
      }
    });
    this.availableBrandsForDepartment = ['All', ...Array.from(brandsSet)];
  }

  private applyFallbackFilter(): void {
    let items = this.getFallbackCatalog();

    // 1. Department Filter
    if (this.activeDepartmentId === 'dog-food') {
      items = items.filter(p =>
        p.petType === 'Dog' &&
        (p.category.toLowerCase().includes('food') ||
         p.name.toLowerCase().includes('food') ||
         p.name.toLowerCase().includes('kibble') ||
         p.name.toLowerCase().includes('rice') ||
         p.name.toLowerCase().includes('gravy'))
      );
    } else if (this.activeDepartmentId === 'dog-care') {
      items = items.filter(p =>
        (p.petType === 'Dog' || p.petType === 'All Pets') &&
        (p.category === 'Grooming' || p.category === 'Hygiene' || p.category === 'Accessories' || p.category === 'Supplements') &&
        !p.name.toLowerCase().includes('cat')
      );
    } else if (this.activeDepartmentId === 'cat-food') {
      items = items.filter(p =>
        p.petType === 'Cat' &&
        (p.category.toLowerCase().includes('food') ||
         p.name.toLowerCase().includes('food') ||
         p.name.toLowerCase().includes('gravy') ||
         p.name.toLowerCase().includes('pate') ||
         p.name.toLowerCase().includes('fish'))
      );
    } else if (this.activeDepartmentId === 'cat-care') {
      items = items.filter(p =>
        (p.petType === 'Cat' || p.petType === 'All Pets') &&
        (p.category === 'Hygiene' || p.category === 'Grooming' || p.category === 'Accessories') &&
        !p.name.toLowerCase().includes('puppy') &&
        !p.name.toLowerCase().includes('dog')
      );
    } else if (this.activeDepartmentId === 'grooming') {
      items = items.filter(p => p.category === 'Grooming' || p.category === 'Hygiene');
    }

    // Populate available brands based on this department
    this.populateAvailableBrands(items);

    // 2. Search Query Filter
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.trim().toLowerCase();
      items = items.filter(p =>
        p.name.toLowerCase().includes(q) ||
        p.brand.toLowerCase().includes(q) ||
        p.category.toLowerCase().includes(q)
      );
    }

    // 3. Brand Filter
    if (this.selectedBrand !== 'All') {
      const b = this.selectedBrand.toLowerCase();
      items = items.filter(p => p.brand.toLowerCase() === b || p.brand.toLowerCase().includes(b));
    }

    // 4. In-Stock Filter
    if (this.inStockOnly) {
      items = items.filter(p => p.inStock);
    }

    // 5. Sort
    if (this.sortBy === 'basePrice,asc') {
      items.sort((a, b) => (a.discountedPrice ?? a.price) - (b.discountedPrice ?? b.price));
    } else if (this.sortBy === 'basePrice,desc') {
      items.sort((a, b) => (b.discountedPrice ?? b.price) - (a.discountedPrice ?? a.price));
    } else {
      items.sort((a, b) => b.id - a.id);
    }

    this.totalElements = items.length;
    this.totalPages = Math.ceil(this.totalElements / this.pageSize);

    // 6. Paginate
    const start = this.currentPage * this.pageSize;
    this.products = items.slice(start, start + this.pageSize);
    this.isLoading = false;

    if (this.products.length === 0 && this.searchQuery.trim()) {
      this.loadSimilarRecommendations();
    } else {
      this.similarProducts = [];
    }
  }

  loadSimilarRecommendations(): void {
    if (!this.searchQuery.trim()) {
      this.similarProducts = [];
      return;
    }
    this.isLoadingSimilar = true;
    const query = encodeURIComponent(this.searchQuery.trim());
    let url = `${environment.apiUrl}/products/recommendations/similar?query=${query}&limit=8`;
    if (this.activeDepartmentId === 'dog-food' || this.activeDepartmentId === 'dog-care') {
      url += `&petType=Dog`;
    } else if (this.activeDepartmentId === 'cat-food' || this.activeDepartmentId === 'cat-care') {
      url += `&petType=Cat`;
    }

    this.http.get<{ success: boolean; data: any[] }>(url).subscribe({
      next: res => {
        this.isLoadingSimilar = false;
        if (res?.success && Array.isArray(res.data) && res.data.length > 0) {
          this.similarProducts = res.data.map(p => this.mapToCard(p));
        } else {
          this.similarProducts = this.getFallbackSimilarProducts(this.searchQuery.trim());
        }
      },
      error: () => {
        this.isLoadingSimilar = false;
        this.similarProducts = this.getFallbackSimilarProducts(this.searchQuery.trim());
      }
    });
  }

  private getFallbackSimilarProducts(query: string): ProductCardData[] {
    const catalog = this.getFallbackCatalog();
    const q = query.toLowerCase();

    let petType: string | null = null;
    if (q.includes('dog') || q.includes('puppy') || q.includes('canine')) petType = 'Dog';
    else if (q.includes('cat') || q.includes('kitten') || q.includes('feline')) petType = 'Cat';

    let category: string | null = null;
    if (q.includes('food') || q.includes('kibble') || q.includes('gravy') || q.includes('biscuit')) category = 'Food';
    else if (q.includes('shampoo') || q.includes('groom') || q.includes('litter') || q.includes('soap')) category = 'Grooming';
    else if (q.includes('toy') || q.includes('chew') || q.includes('ball')) category = 'Accessories';

    let matches = catalog.filter(p => p.inStock);

    if (petType) {
      matches = matches.filter(p => p.petType === petType || p.petType === 'All Pets');
    }
    if (category) {
      const catMatches = matches.filter(p => p.category.toLowerCase().includes(category.toLowerCase()));
      if (catMatches.length > 0) {
        matches = catMatches;
      }
    }

    return matches.slice(0, 8);
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
    const price = event.variant ? (event.variant.discountedPrice || event.variant.price) : (event.product.discountedPrice || event.product.price);
    this.cartService.addItem(event.product.id, event.variant?.id, 1, {
      productName: event.product.name,
      brandName: event.product.brand,
      primaryImageUrl: event.product.imageUrl,
      unitPrice: price,
      variantName: event.variant?.name,
      availableStock: event.variant?.stock || event.product.stockQuantity || 15,
      returnAllowed: event.product.returnAllowed ?? true,
      returnWindowDays: 7
    }).subscribe();
    this.cartMessage = `Added "${event.product.name}" to cart!`;
    setTimeout(() => { this.cartMessage = ''; }, 3000);
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
