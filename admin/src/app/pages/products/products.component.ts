import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../environments/environment';
import { BackButtonComponent } from '../../components/back-button/back-button.component';
import { PaginationComponent } from '../../components/pagination/pagination.component';

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
  isArchived?: boolean;
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
  imports: [CommonModule, FormsModule, BackButtonComponent, PaginationComponent],
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
  selectedCategoryId = 'ALL';
  statusFilter: 'ALL' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED' = 'ALL';
  sortBy: 'name-asc' | 'name-desc' | 'price-asc' | 'price-desc' | 'stock-asc' | 'stock-desc' = 'name-asc';

  // Remove Product Modal State
  showRemoveProductModal = false;
  selectedProductForRemoval: ProductItem | null = null;
  isRemovingProduct = false;
  removeErrorMessage = '';
  removeSuccessMessage = '';

  // Catalog Pagination
  catalogPage = 0;
  catalogPageSize = 10;

  get sortedProducts(): ProductItem[] {
    let list = [...this.products];

    // Status Filter
    if (this.statusFilter === 'ACTIVE') {
      list = list.filter(p => p.isActive && !p.isArchived);
    } else if (this.statusFilter === 'INACTIVE') {
      list = list.filter(p => !p.isActive && !p.isArchived);
    } else if (this.statusFilter === 'ARCHIVED') {
      list = list.filter(p => !!p.isArchived);
    }

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

  get paginatedProducts(): ProductItem[] {
    const start = this.catalogPage * this.catalogPageSize;
    return this.sortedProducts.slice(start, start + this.catalogPageSize);
  }

  get totalCatalogElements(): number {
    return this.sortedProducts.length;
  }

  onCatalogPageChange(page: number): void {
    this.catalogPage = page;
  }

  onCatalogPageSizeChange(size: number): void {
    this.catalogPageSize = size;
    this.catalogPage = 0;
  }

  // Pricing Table Pagination
  pricingPage = 0;
  pricingPageSize = 10;

  get paginatedPricingProducts(): ProductItem[] {
    const start = this.pricingPage * this.pricingPageSize;
    return this.products.slice(start, start + this.pricingPageSize);
  }

  get totalPricingElements(): number {
    return this.products.length;
  }

  onPricingPageChange(page: number): void {
    this.pricingPage = page;
  }

  onPricingPageSizeChange(size: number): void {
    this.pricingPageSize = size;
    this.pricingPage = 0;
  }

  // Tax Configurations
  taxConfigs: TaxConfigItem[] = [];
  isLoadingTax = false;
  taxSuccessMessage = '';
  taxErrorMessage = '';
  editingTaxId: number | null = null;
  editTaxModel: Partial<TaxConfigItem> = {};
  showAddTaxModal = false;
  newTaxModel: Partial<TaxConfigItem> = { taxName: '', hsnCode: '', cgstRate: 9, sgstRate: 9, isActive: true };
  isSavingTax = false;

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

  // Product Creation Images (Min 2, Max 6)
  newProductImages: { url: string; altText: string }[] = [];
  newImageUrlInput = '';
  newImageAltInput = '';
  isUploadingNewImage = false;
  uploadProgressText = '';

  // Gallery Modal for Existing Products
  showGalleryModal = false;
  selectedGalleryProduct: ProductItem | null = null;
  galleryImages: { id: number; productId: number; imageUrl: string; altText: string; displayOrder: number; isPrimary: boolean; viewType?: string }[] = [];
  isLoadingGallery = false;
  galleryErrorMessage = '';
  gallerySuccessMessage = '';
  galleryAddUrl = '';
  galleryAddAlt = '';
  isUploadingGalleryImage = false;
  galleryUploadProgressText = '';
  editingGalleryImageId: number | null = null;
  replaceImageUrl = '';

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
    this.catalogPage = 0;
    let url = `${environment.apiUrl}/admin/products?page=0&size=200`;
    if (this.searchQuery.trim()) url += `&query=${encodeURIComponent(this.searchQuery.trim())}`;
    if (this.selectedCategoryId !== 'ALL') url += `&categoryId=${encodeURIComponent(this.selectedCategoryId)}`;

    this.http.get<any>(url).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data?.content) {
          this.products = res.data.content;
        }
      },
      error: () => {
        // Fallback to public products endpoint if needed
        this.http.get<any>(`${environment.apiUrl}/products?page=0&size=200`).subscribe({
          next: fallbackRes => {
            this.isLoading = false;
            if (fallbackRes?.success && fallbackRes.data?.content) {
              this.products = fallbackRes.data.content;
            }
          },
          error: () => {
            this.isLoading = false;
          }
        });
      }
    });
  }

  openRemoveProductModal(p: ProductItem): void {
    this.selectedProductForRemoval = p;
    this.removeErrorMessage = '';
    this.removeSuccessMessage = '';
    this.showRemoveProductModal = true;
  }

  closeRemoveProductModal(): void {
    if (this.isRemovingProduct) return;
    this.showRemoveProductModal = false;
    this.selectedProductForRemoval = null;
    this.removeErrorMessage = '';
  }

  confirmRemoveProduct(): void {
    if (!this.selectedProductForRemoval || this.isRemovingProduct) return;
    this.isRemovingProduct = true;
    this.removeErrorMessage = '';

    const id = this.selectedProductForRemoval.id;
    this.http.delete<any>(`${environment.apiUrl}/admin/products/${id}`).subscribe({
      next: res => {
        this.isRemovingProduct = false;
        this.showRemoveProductModal = false;
        this.selectedProductForRemoval = null;
        const msg = res?.data?.message || res?.message || 'Product archived successfully.';
        this.removeSuccessMessage = msg;
        setTimeout(() => this.removeSuccessMessage = '', 6000);
        this.fetchProducts();
      },
      error: err => {
        this.isRemovingProduct = false;
        this.removeErrorMessage = err.error?.message || 'Failed to archive product. Please try again.';
      }
    });
  }

  restoreProduct(p: ProductItem): void {
    this.http.post<any>(`${environment.apiUrl}/admin/products/${p.id}/restore`, {}).subscribe({
      next: res => {
        this.removeSuccessMessage = `Product "${p.name}" restored to active catalog.`;
        setTimeout(() => this.removeSuccessMessage = '', 6000);
        this.fetchProducts();
      },
      error: err => {
        alert(err.error?.message || 'Failed to restore product.');
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

  calculateTotalGst(cgst?: number | null, sgst?: number | null): string {
    const c = Number(cgst) || 0;
    const s = Number(sgst) || 0;
    return (c + s).toFixed(1);
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

  startEditTax(tax: TaxConfigItem): void {
    this.editingTaxId = tax.id;
    this.editTaxModel = {
      id: tax.id,
      taxName: tax.taxName,
      hsnCode: tax.hsnCode,
      cgstRate: tax.cgstRate,
      sgstRate: tax.sgstRate,
      isActive: tax.isActive
    };
    this.taxErrorMessage = '';
  }

  cancelEditTax(): void {
    this.editingTaxId = null;
    this.editTaxModel = {};
    this.taxErrorMessage = '';
  }

  saveTaxConfig(id: number): void {
    if (!this.editTaxModel.taxName?.trim()) {
      this.taxErrorMessage = 'Tax description name cannot be blank.';
      return;
    }
    if (!this.editTaxModel.hsnCode?.trim()) {
      this.taxErrorMessage = 'HSN code cannot be blank.';
      return;
    }
    const cgst = Number(this.editTaxModel.cgstRate) || 0;
    const sgst = Number(this.editTaxModel.sgstRate) || 0;
    if (cgst < 0 || sgst < 0) {
      this.taxErrorMessage = 'Tax rates cannot be negative.';
      return;
    }

    this.isSavingTax = true;
    this.taxErrorMessage = '';

    const payload = {
      taxName: this.editTaxModel.taxName.trim(),
      hsnCode: this.editTaxModel.hsnCode.trim(),
      cgstRate: cgst,
      sgstRate: sgst,
      igstRate: cgst + sgst,
      isActive: this.editTaxModel.isActive
    };

    this.http.put<any>(`${environment.apiUrl}/admin/tax/configurations/${id}`, payload).subscribe({
      next: res => {
        this.isSavingTax = false;
        if (res?.success && res.data) {
          const index = this.taxConfigs.findIndex(t => t.id === id);
          if (index !== -1) {
            this.taxConfigs[index] = res.data;
          }
          this.editingTaxId = null;
          this.editTaxModel = {};
          this.taxSuccessMessage = `GST configuration "${res.data.taxName}" updated successfully.`;
          setTimeout(() => { this.taxSuccessMessage = ''; }, 3500);
        }
      },
      error: err => {
        this.isSavingTax = false;
        this.taxErrorMessage = err.error?.message || 'Failed to update GST configuration.';
      }
    });
  }

  openAddTaxModal(): void {
    this.newTaxModel = {
      taxName: '',
      hsnCode: '',
      cgstRate: 9,
      sgstRate: 9,
      isActive: true
    };
    this.taxErrorMessage = '';
    this.showAddTaxModal = true;
  }

  saveNewTaxConfig(): void {
    if (!this.newTaxModel.taxName?.trim()) {
      this.taxErrorMessage = 'Tax description name cannot be blank.';
      return;
    }
    if (!this.newTaxModel.hsnCode?.trim()) {
      this.taxErrorMessage = 'HSN code cannot be blank.';
      return;
    }
    const cgst = Number(this.newTaxModel.cgstRate) || 0;
    const sgst = Number(this.newTaxModel.sgstRate) || 0;

    this.isSavingTax = true;
    this.taxErrorMessage = '';

    const payload = {
      taxName: this.newTaxModel.taxName.trim(),
      hsnCode: this.newTaxModel.hsnCode.trim(),
      cgstRate: cgst,
      sgstRate: sgst,
      igstRate: cgst + sgst,
      isActive: this.newTaxModel.isActive ?? true
    };

    this.http.post<any>(`${environment.apiUrl}/admin/tax/configurations`, payload).subscribe({
      next: res => {
        this.isSavingTax = false;
        if (res?.success && res.data) {
          this.taxConfigs.push(res.data);
          this.showAddTaxModal = false;
          this.taxSuccessMessage = `New GST configuration "${res.data.taxName}" created successfully.`;
          setTimeout(() => { this.taxSuccessMessage = ''; }, 3500);
        }
      },
      error: err => {
        this.isSavingTax = false;
        this.taxErrorMessage = err.error?.message || 'Failed to create GST configuration.';
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
    this.newProductImages = [
      { url: 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?auto=format&fit=crop&w=600&q=80', altText: 'Front View' },
      { url: 'https://images.unsplash.com/photo-1548767797-d8c844163c4c?auto=format&fit=crop&w=600&q=80', altText: 'Back View' }
    ];
    this.newImageUrlInput = '';
    this.newImageAltInput = '';
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

  // Image helpers for product creation
  addImageToNewProduct(): void {
    if (!this.newImageUrlInput.trim()) return;
    if (this.newProductImages.length >= 6) {
      this.modalErrorMessage = 'Maximum of 6 images allowed per product.';
      this.scrollToModalTop();
      return;
    }
    const alt = this.newImageAltInput.trim() ||
      (this.newProductImages.length === 0 ? 'Front View' : (this.newProductImages.length === 1 ? 'Back View' : `Image ${this.newProductImages.length + 1}`));
    this.newProductImages.push({ url: this.newImageUrlInput.trim(), altText: alt });
    this.newImageUrlInput = '';
    this.newImageAltInput = '';
    this.modalErrorMessage = '';
  }

  removeImageFromNewProduct(index: number): void {
    this.newProductImages.splice(index, 1);
  }

  scrollToModalTop(): void {
    setTimeout(() => {
      const modalCard = document.querySelector('.modal-card');
      if (modalCard) {
        modalCard.scrollTo({ top: 0, behavior: 'smooth' });
      }
      const alertEl = document.querySelector('.modal-card .alert-danger');
      if (alertEl) {
        alertEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    }, 60);
  }

  scrollToGalleryTop(): void {
    setTimeout(() => {
      const galleryCard = document.querySelector('.gallery-modal-card');
      if (galleryCard) {
        galleryCard.scrollTo({ top: 0, behavior: 'smooth' });
      }
      const alertEl = document.querySelector('.gallery-modal-card .alert-danger');
      if (alertEl) {
        alertEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    }, 60);
  }

  async onNewImageFileUpload(event: any): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = input?.files;
    if (!files || files.length === 0) return;

    const availableSlots = 6 - this.newProductImages.length;
    if (availableSlots <= 0) {
      this.modalErrorMessage = 'Maximum of 6 images allowed per product.';
      this.scrollToModalTop();
      input.value = '';
      return;
    }

    const filesToUpload = Array.from(files).slice(0, availableSlots);
    if (files.length > availableSlots) {
      this.modalErrorMessage = `Only ${availableSlots} image(s) could be added (maximum is 6 per product).`;
    } else {
      this.modalErrorMessage = '';
    }

    this.isUploadingNewImage = true;
    this.uploadProgressText = `Uploading 1 of ${filesToUpload.length}...`;

    const failureErrors: string[] = [];

    try {
      for (let i = 0; i < filesToUpload.length; i++) {
        const file = filesToUpload[i];
        this.uploadProgressText = `Uploading ${i + 1} of ${filesToUpload.length} image(s)...`;

        const formData = new FormData();
        formData.append('file', file);

        try {
          const res: any = await this.http.post<any>(`${environment.apiUrl}/admin/media/upload`, formData).toPromise();
          if (res?.success && res.data?.url) {
            const currentCount = this.newProductImages.length;
            const alt = currentCount === 0 ? 'Front View' : (currentCount === 1 ? 'Back View' : `Image ${currentCount + 1}`);
            this.newProductImages.push({ url: res.data.url, altText: alt });
          } else {
            failureErrors.push(`Failed to upload ${file.name}`);
          }
        } catch (uploadErr: any) {
          const msg = uploadErr?.error?.message || `Failed to upload ${file.name}`;
          failureErrors.push(msg);
        }
      }

      if (failureErrors.length > 0) {
        this.modalErrorMessage = failureErrors.join(' | ');
        this.scrollToModalTop();
      }
    } finally {
      this.isUploadingNewImage = false;
      this.uploadProgressText = '';
      input.value = '';
    }
  }

  async saveProduct(): Promise<void> {
    if (!this.productForm.name?.trim() || !this.productForm.basePrice) {
      this.modalErrorMessage = 'Please fill all required fields: Product Name and Base Price.';
      this.scrollToModalTop();
      return;
    }

    if (this.newProductImages.length < 2) {
      this.modalErrorMessage = 'Every product requires at least 2 images: Front View (Image 1) and Back View (Image 2).';
      this.scrollToModalTop();
      return;
    }

    if (this.newProductImages.length > 6) {
      this.modalErrorMessage = 'A maximum of 6 images is allowed per product.';
      this.scrollToModalTop();
      return;
    }

    try {
      // 1. Auto-create Brand if "+ Others" selected
      if (this.productForm.brandId === 'OTHER' || String(this.productForm.brandId) === 'OTHER') {
        if (!this.customBrandName.trim()) {
          this.modalErrorMessage = 'Please enter a name for the new brand.';
          this.scrollToModalTop();
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
          this.scrollToModalTop();
          return;
        }
      }

      // 2. Auto-create Category if "+ Others" selected
      if (this.productForm.categoryId === 'OTHER' || String(this.productForm.categoryId) === 'OTHER') {
        if (!this.customCategoryName.trim()) {
          this.modalErrorMessage = 'Please enter a name for the new category.';
          this.scrollToModalTop();
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
          this.scrollToModalTop();
          return;
        }
      }

      // 3. Save Product with validated gallery images
      const payload = {
        ...this.productForm,
        brandId: Number(this.productForm.brandId),
        categoryId: Number(this.productForm.categoryId),
        basePrice: Number(this.productForm.basePrice),
        discountedPrice: this.productForm.discountedPrice ? Number(this.productForm.discountedPrice) : Number(this.productForm.basePrice),
        imageUrls: this.newProductImages.map(img => img.url)
      };

      this.http.post<any>(`${environment.apiUrl}/admin/products`, payload).subscribe({
        next: res => {
          if (res?.success) {
            this.showModal = false;
            this.fetchProducts();
          }
        },
        error: err => {
          const details = Array.isArray(err.error?.errors) && err.error.errors.length > 0
            ? ': ' + err.error.errors.join(', ')
            : '';
          this.modalErrorMessage = (err.error?.message || 'Failed to save product.') + details;
          this.scrollToModalTop();
        }
      });
    } catch (e: any) {
      this.modalErrorMessage = e?.error?.message || e?.message || 'Error auto-creating brand or category';
      this.scrollToModalTop();
    }
  }

  // ========================================================
  // Gallery Management Modal for Existing Products
  // ========================================================
  openGalleryModal(p: ProductItem): void {
    this.selectedGalleryProduct = p;
    this.galleryImages = [];
    this.galleryErrorMessage = '';
    this.gallerySuccessMessage = '';
    this.galleryAddUrl = '';
    this.galleryAddAlt = '';
    this.editingGalleryImageId = null;
    this.replaceImageUrl = '';
    this.showGalleryModal = true;
    this.fetchGalleryImages(p.id);
  }

  fetchGalleryImages(productId: number): void {
    this.isLoadingGallery = true;
    this.http.get<any>(`${environment.apiUrl}/admin/products/${productId}/images`).subscribe({
      next: res => {
        this.isLoadingGallery = false;
        if (res?.success && res.data) {
          this.galleryImages = res.data;
        }
      },
      error: err => {
        this.isLoadingGallery = false;
        this.galleryErrorMessage = err.error?.message || 'Failed to load product images.';
      }
    });
  }

  addGalleryImage(): void {
    if (!this.selectedGalleryProduct || !this.galleryAddUrl.trim()) return;
    if (this.galleryImages.length >= 6) {
      this.galleryErrorMessage = 'Maximum 6 images allowed per product.';
      return;
    }

    const payload = {
      imageUrl: this.galleryAddUrl.trim(),
      altText: this.galleryAddAlt.trim() || 'Product Image',
      displayOrder: this.galleryImages.length,
      isPrimary: this.galleryImages.length === 0
    };

    this.http.post<any>(`${environment.apiUrl}/admin/products/${this.selectedGalleryProduct.id}/images`, payload).subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.galleryAddUrl = '';
          this.galleryAddAlt = '';
          this.gallerySuccessMessage = 'Image added successfully!';
          setTimeout(() => { this.gallerySuccessMessage = ''; }, 3000);
          this.fetchGalleryImages(this.selectedGalleryProduct!.id);
        }
      },
      error: err => {
        this.galleryErrorMessage = err.error?.message || 'Failed to add image.';
      }
    });
  }

  async onGalleryFileUpload(event: any): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = input?.files;
    if (!files || files.length === 0 || !this.selectedGalleryProduct) return;

    const availableSlots = 6 - this.galleryImages.length;
    if (availableSlots <= 0) {
      this.galleryErrorMessage = 'Maximum 6 images allowed per product.';
      this.scrollToGalleryTop();
      input.value = '';
      return;
    }

    const filesToUpload = Array.from(files).slice(0, availableSlots);
    this.isUploadingGalleryImage = true;
    this.galleryUploadProgressText = `Uploading 1 of ${filesToUpload.length}...`;
    this.galleryErrorMessage = '';
    let failCount = 0;

    try {
      for (let i = 0; i < filesToUpload.length; i++) {
        const file = filesToUpload[i];
        this.galleryUploadProgressText = `Uploading ${i + 1} of ${filesToUpload.length}...`;
        const formData = new FormData();
        formData.append('file', file);

        try {
          const res: any = await this.http.post<any>(`${environment.apiUrl}/admin/media/upload`, formData).toPromise();
          if (res?.success && res.data?.url) {
            await this.http.post<any>(`${environment.apiUrl}/admin/products/${this.selectedGalleryProduct.id}/images`, {
              imageUrl: res.data.url,
              altText: `Image ${this.galleryImages.length + 1}`,
              displayOrder: this.galleryImages.length,
              isPrimary: this.galleryImages.length === 0
            }).toPromise();
          } else {
            failCount++;
          }
        } catch (err: any) {
          failCount++;
          this.galleryErrorMessage = err.error?.message || `Failed to upload ${file.name}`;
        }
      }

      if (failCount > 0 && !this.galleryErrorMessage) {
        this.galleryErrorMessage = `${failCount} image(s) failed to upload.`;
      }
      if (this.galleryErrorMessage) {
        this.scrollToGalleryTop();
      }
      this.fetchGalleryImages(this.selectedGalleryProduct.id);
    } finally {
      this.isUploadingGalleryImage = false;
      this.galleryUploadProgressText = '';
      input.value = '';
    }
  }

  startReplaceGalleryImage(img: any): void {
    this.editingGalleryImageId = img.id;
    this.replaceImageUrl = img.imageUrl;
  }

  saveReplaceGalleryImage(imgId: number): void {
    if (!this.selectedGalleryProduct || !this.replaceImageUrl.trim()) return;

    this.http.put<any>(`${environment.apiUrl}/admin/products/${this.selectedGalleryProduct.id}/images/${imgId}/replace`, {
      imageUrl: this.replaceImageUrl.trim()
    }).subscribe({
      next: () => {
        this.editingGalleryImageId = null;
        this.replaceImageUrl = '';
        this.gallerySuccessMessage = 'Image replaced successfully!';
        setTimeout(() => { this.gallerySuccessMessage = ''; }, 3000);
        this.fetchGalleryImages(this.selectedGalleryProduct!.id);
      },
      error: err => {
        this.galleryErrorMessage = err.error?.message || 'Failed to replace image.';
      }
    });
  }

  moveGalleryImageUp(index: number): void {
    if (index <= 0 || !this.selectedGalleryProduct) return;
    const temp = this.galleryImages[index];
    this.galleryImages[index] = this.galleryImages[index - 1];
    this.galleryImages[index - 1] = temp;
    this.saveGalleryReorder();
  }

  moveGalleryImageDown(index: number): void {
    if (index >= this.galleryImages.length - 1 || !this.selectedGalleryProduct) return;
    const temp = this.galleryImages[index];
    this.galleryImages[index] = this.galleryImages[index + 1];
    this.galleryImages[index + 1] = temp;
    this.saveGalleryReorder();
  }

  saveGalleryReorder(): void {
    if (!this.selectedGalleryProduct) return;
    const ids = this.galleryImages.map(img => img.id);

    this.http.put<any>(`${environment.apiUrl}/admin/products/${this.selectedGalleryProduct.id}/images/reorder`, ids).subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.galleryImages = res.data;
          this.gallerySuccessMessage = 'Image order updated!';
          setTimeout(() => { this.gallerySuccessMessage = ''; }, 2500);
        }
      },
      error: err => {
        this.galleryErrorMessage = err.error?.message || 'Failed to reorder images.';
      }
    });
  }

  deleteGalleryImage(imgId: number): void {
    if (!this.selectedGalleryProduct) return;
    if (this.galleryImages.length <= 2) {
      this.galleryErrorMessage = 'Cannot delete. Products must retain at least 2 images: Front View and Back View.';
      return;
    }

    if (!confirm('Are you sure you want to delete this product image?')) {
      return;
    }

    this.http.delete<any>(`${environment.apiUrl}/admin/products/${this.selectedGalleryProduct.id}/images/${imgId}`).subscribe({
      next: () => {
        this.gallerySuccessMessage = 'Image deleted successfully!';
        setTimeout(() => { this.gallerySuccessMessage = ''; }, 3000);
        this.fetchGalleryImages(this.selectedGalleryProduct!.id);
      },
      error: err => {
        this.galleryErrorMessage = err.error?.message || 'Failed to delete image.';
      }
    });
  }
}
