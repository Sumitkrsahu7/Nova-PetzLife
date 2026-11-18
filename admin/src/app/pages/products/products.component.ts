import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../environments/environment';

export interface ProductItem {
  id: number;
  sku: string;
  name: string;
  brandName: string;
  categoryName: string;
  petType: string;
  basePrice: number;
  discountedPrice?: number;
  isActive: boolean;
  stock?: number;
}

export interface TaxConfigItem {
  id: number;
  taxName: string;
  hsnCode: string;
  cgstRate: number;
  sgstRate: number;
  igstRate: number;
  isActive: boolean;
}

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './products.component.html',
  styleUrls: ['./products.component.scss']
})
export class ProductsComponent implements OnInit {
  activeTab: 'catalog' | 'pricing' = 'catalog';
  products: ProductItem[] = [];
  categories: any[] = [];
  brands: any[] = [];
  isLoading = false;
  searchQuery = '';
  selectedPetType = 'ALL';
  sortBy: 'name-asc' | 'name-desc' | 'price-asc' | 'price-desc' | 'stock-asc' | 'stock-desc' = 'name-asc';

  get sortedProducts(): ProductItem[] {
    let list = [...this.products];
    switch (this.sortBy) {
      case 'name-asc':
        return list.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
      case 'name-desc':
        return list.sort((a, b) => (b.name || '').localeCompare(a.name || ''));
      case 'price-asc':
        return list.sort((a, b) => (a.discountedPrice ?? a.basePrice) - (b.discountedPrice ?? b.basePrice));
      case 'price-desc':
        return list.sort((a, b) => (b.discountedPrice ?? b.basePrice) - (a.discountedPrice ?? a.basePrice));
      case 'stock-asc':
        return list.sort((a, b) => (a.stock ?? 0) - (b.stock ?? 0));
      case 'stock-desc':
        return list.sort((a, b) => (b.stock ?? 0) - (a.stock ?? 0));
      default:
        return list;
    }
  }

  // Tax Configurations
  taxConfigs: TaxConfigItem[] = [];
  isLoadingTax = false;
  taxSuccessMessage = '';

  // Price Editing Modal
  showPriceModal = false;
  selectedPriceProduct: ProductItem | null = null;
  editBasePrice = 0;
  editDiscountedPrice = 0;
  priceModalError = '';

  // Add/Edit Modal
  showModal = false;
  isEditing = false;
  editingId: number | null = null;
  customBrandName = '';
  customCategoryName = '';
  productForm = {
    name: '',
    description: '',
    sku: '',
    barcode: '',
    petType: 'Dog',
    lifeStage: 'Adult',
    productType: 'Food',
    categoryId: '1' as any,
    brandId: '1' as any,
    basePrice: 0,
    discountedPrice: 0,
    initialStock: 10,
    isFeatured: false
  };
  modalErrorMessage = '';

  constructor(
    private http: HttpClient,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.fetchCatalogMeta();
    this.fetchProducts();
    this.fetchTaxConfigs();

    this.route.queryParams.subscribe(params => {
      if (params['tab'] === 'pricing' || window.location.pathname.includes('/pricing')) {
        this.activeTab = 'pricing';
      }
      if (params['action'] === 'new') {
        setTimeout(() => this.openCreateModal(), 200);
      }
    });
  }

  fetchCatalogMeta(): void {
    this.http.get<any>(`${environment.apiUrl}/categories/flat`).subscribe(res => {
      if (res?.success && res.data) {
        this.categories = res.data;
        if (!this.productForm.categoryId && this.categories.length > 0) {
          this.productForm.categoryId = this.categories[0].id;
        }
      }
    });
    this.http.get<any>(`${environment.apiUrl}/brands`).subscribe(res => {
      if (res?.success && res.data) {
        this.brands = res.data;
        if (!this.productForm.brandId && this.brands.length > 0) {
          this.productForm.brandId = this.brands[0].id;
        }
      }
    });
  }

  fetchProducts(): void {
    this.isLoading = true;
    let url = `${environment.apiUrl}/products?page=0&size=50`;
    if (this.searchQuery.trim()) url += `&query=${encodeURIComponent(this.searchQuery.trim())}`;
    if (this.selectedPetType !== 'ALL') url += `&petType=${encodeURIComponent(this.selectedPetType)}`;

    this.http.get<any>(url).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data?.content) {
          this.products = res.data.content;
        }
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  fetchTaxConfigs(): void {
    this.isLoadingTax = true;
    this.http.get<any>(`${environment.apiUrl}/admin/tax/configurations`).subscribe({
      next: res => {
        this.isLoadingTax = false;
        if (res?.success && res.data) {
          this.taxConfigs = res.data;
        }
      },
      error: () => {
        this.isLoadingTax = false;
      }
    });
  }

  toggleTaxStatus(tax: TaxConfigItem): void {
    this.http.patch<any>(`${environment.apiUrl}/admin/tax/configurations/${tax.id}/status`, {}).subscribe({
      next: res => {
        if (res?.success) {
          tax.isActive = !tax.isActive;
          this.taxSuccessMessage = `GST rate "${tax.taxName}" status updated to ${tax.isActive ? 'Active' : 'Inactive'}.`;
          setTimeout(() => { this.taxSuccessMessage = ''; }, 3500);
        }
      }
    });
  }

  openPriceModal(p: ProductItem): void {
    this.selectedPriceProduct = p;
    this.editBasePrice = p.basePrice;
    this.editDiscountedPrice = p.discountedPrice || p.basePrice;
    this.priceModalError = '';
    this.showPriceModal = true;
  }

  saveQuickPrice(): void {
    if (!this.selectedPriceProduct || this.editBasePrice <= 0) {
      this.priceModalError = 'Base price must be greater than zero.';
      return;
    }

    const payload = {
      basePrice: Number(this.editBasePrice),
      discountedPrice: Number(this.editDiscountedPrice)
    };

    // Update product via update endpoint
    this.http.put<any>(`${environment.apiUrl}/admin/products/${this.selectedPriceProduct.id}`, {
      name: this.selectedPriceProduct.name,
      sku: this.selectedPriceProduct.sku,
      brandId: 1,
      categoryId: 1,
      basePrice: payload.basePrice,
      discountedPrice: payload.discountedPrice
    }).subscribe({
      next: res => {
        if (this.selectedPriceProduct) {
          this.selectedPriceProduct.basePrice = payload.basePrice;
          this.selectedPriceProduct.discountedPrice = payload.discountedPrice;
        }
        this.showPriceModal = false;
        this.fetchProducts();
      },
      error: err => {
        this.priceModalError = err.error?.message || 'Failed to update pricing.';
      }
    });
  }

  toggleStatus(p: ProductItem): void {
    this.http.patch<any>(`${environment.apiUrl}/admin/products/${p.id}/status`, {}).subscribe({
      next: res => {
        if (res?.success) {
          p.isActive = !p.isActive;
        }
      }
    });
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.editingId = null;
    this.customBrandName = '';
    this.customCategoryName = '';
    this.productForm = {
      name: '',
      description: '',
      sku: 'SKU-' + Math.random().toString(36).substring(2, 7).toUpperCase(),
      barcode: '',
      petType: 'Dog',
      lifeStage: 'Adult',
      productType: 'Food',
      categoryId: this.categories[0]?.id || 1,
      brandId: this.brands[0]?.id || 1,
      basePrice: 499,
      discountedPrice: 449,
      initialStock: 15,
      isFeatured: false
    };
    this.modalErrorMessage = '';
    this.showModal = true;
  }

  async saveProduct(): Promise<void> {
    if (!this.productForm.name || !this.productForm.basePrice) {
      this.modalErrorMessage = 'Please fill all required fields';
      return;
    }

    try {
      // 1. Auto-create Brand if "+ Others" selected
      if (this.productForm.brandId === 'OTHER' || String(this.productForm.brandId) === 'OTHER') {
        if (!this.customBrandName.trim()) {
          this.modalErrorMessage = 'Please enter a name for the new brand.';
          return;
        }
        const bPayload = {
          name: this.customBrandName.trim(),
          slug: this.customBrandName.trim().toLowerCase().replace(/[\s&]+/g, '-'),
          description: this.customBrandName.trim() + ' Products',
          isActive: true
        };
        const bRes: any = await this.http.post<any>(`${environment.apiUrl}/admin/brands`, bPayload).toPromise();
        if (bRes?.success && bRes.data?.id) {
          this.brands.push(bRes.data);
          this.productForm.brandId = bRes.data.id;
        } else {
          this.modalErrorMessage = 'Failed to create new brand.';
          return;
        }
      }

      // 2. Auto-create Category if "+ Others" selected
      if (this.productForm.categoryId === 'OTHER' || String(this.productForm.categoryId) === 'OTHER') {
        if (!this.customCategoryName.trim()) {
          this.modalErrorMessage = 'Please enter a name for the new category.';
          return;
        }
        const cPayload = {
          name: this.customCategoryName.trim(),
          slug: this.customCategoryName.trim().toLowerCase().replace(/[\s&]+/g, '-'),
          description: this.customCategoryName.trim(),
          parentId: null
        };
        const cRes: any = await this.http.post<any>(`${environment.apiUrl}/admin/categories`, cPayload).toPromise();
        if (cRes?.success && cRes.data?.id) {
          this.categories.push(cRes.data);
          this.productForm.categoryId = cRes.data.id;
        } else {
          this.modalErrorMessage = 'Failed to create new category.';
          return;
        }
      }

      // 3. Save Product
      const payload = {
        ...this.productForm,
        brandId: Number(this.productForm.brandId),
        categoryId: Number(this.productForm.categoryId),
        basePrice: Number(this.productForm.basePrice),
        discountedPrice: this.productForm.discountedPrice ? Number(this.productForm.discountedPrice) : Number(this.productForm.basePrice)
      };

      this.http.post<any>(`${environment.apiUrl}/admin/products`, payload).subscribe({
        next: res => {
          if (res?.success) {
            this.showModal = false;
            this.fetchProducts();
          }
        },
        error: err => {
          this.modalErrorMessage = err.error?.message || 'Failed to save product';
        }
      });
    } catch (e: any) {
      this.modalErrorMessage = e?.error?.message || 'Error auto-creating brand or category';
    }
  }
}
