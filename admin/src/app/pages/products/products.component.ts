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
  sortBy: 'name-asc' | 'name-desc' | 'price-asc' | 'price-desc' | 'stock-asc' | 'stock-desc' = 'name-asc';

  // Catalog Pagination
  catalogPage = 0;
  catalogPageSize = 10;

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
    let url = `${environment.apiUrl}/products?page=0&size=200`;
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
      return;
    }
    const alt = this.newImageAltInput.trim() ||
      (this.newProductImages.length === 0 ? 'Front View' : (this.newProductImages.length === 1 ? 'Back View' : 'Additional View'));
    this.newProductImages.push({ url: this.newImageUrlInput.trim(), altText: alt });
    this.newImageUrlInput = '';
    this.newImageAltInput = '';
    this.modalErrorMessage = '';
  }

  removeImageFromNewProduct(index: number): void {
    this.newProductImages.splice(index, 1);
  }

  onNewImageFileUpload(event: any): void {
    const file = event.target.files?.[0];
    if (!file) return;
    if (this.newProductImages.length >= 6) {
      this.modalErrorMessage = 'Maximum of 6 images allowed per product.';
      return;
    }

    const formData = new FormData();
    formData.append('file', file);
    this.isUploadingNewImage = true;
    this.modalErrorMessage = '';

    this.http.post<any>(`${environment.apiUrl}/admin/media/upload`, formData).subscribe({
      next: res => {
        this.isUploadingNewImage = false;
        if (res?.success && res.data?.url) {
          const alt = this.newProductImages.length === 0 ? 'Front View' : (this.newProductImages.length === 1 ? 'Back View' : 'Additional View');
          this.newProductImages.push({ url: res.data.url, altText: alt });
        }
      },
      error: err => {
        this.isUploadingNewImage = false;
        this.modalErrorMessage = err.error?.message || 'Failed to upload image file.';
      }
    });
  }

  async saveProduct(): Promise<void> {
    if (!this.productForm.name || !this.productForm.basePrice) {
      this.modalErrorMessage = 'Please fill all required fields';
      return;
    }

    if (this.newProductImages.length < 2) {
      this.modalErrorMessage = 'Every product requires at least 2 images: Front View (Image 1) and Back View (Image 2).';
      return;
    }

    if (this.newProductImages.length > 6) {
      this.modalErrorMessage = 'A maximum of 6 images is allowed per product.';
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
          this.modalErrorMessage = err.error?.message || 'Failed to save product';
        }
      });
    } catch (e: any) {
      this.modalErrorMessage = e?.error?.message || 'Error auto-creating brand or category';
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

  onGalleryFileUpload(event: any): void {
    const file = event.target.files?.[0];
    if (!file || !this.selectedGalleryProduct) return;
    if (this.galleryImages.length >= 6) {
      this.galleryErrorMessage = 'Maximum 6 images allowed per product.';
      return;
    }

    const formData = new FormData();
    formData.append('file', file);
    this.isUploadingGalleryImage = true;
    this.galleryErrorMessage = '';

    this.http.post<any>(`${environment.apiUrl}/admin/media/upload`, formData).subscribe({
      next: res => {
        this.isUploadingGalleryImage = false;
        if (res?.success && res.data?.url) {
          this.galleryAddUrl = res.data.url;
          this.addGalleryImage();
        }
      },
      error: err => {
        this.isUploadingGalleryImage = false;
        this.galleryErrorMessage = err.error?.message || 'Failed to upload image file.';
      }
    });
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
