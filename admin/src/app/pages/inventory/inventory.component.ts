import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../environments/environment';
import { BackButtonComponent } from '../../components/back-button/back-button.component';
import { PaginationComponent } from '../../components/pagination/pagination.component';

export interface InventoryRecord {
  id: number;
  productId: number;
  productName: string;
  sku: string;
  brandName: string;
  availableStock: number;
  reservedStock: number;
  minThreshold: number;
  isLowStock: boolean;
}

@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [CommonModule, FormsModule, BackButtonComponent, PaginationComponent],
  templateUrl: './inventory.component.html',
  styleUrls: ['./inventory.component.scss']
})
export class InventoryComponent implements OnInit {
  inventoryList: InventoryRecord[] = [];
  isLoading = false;
  showLowStockOnly = false;
  searchQuery = '';
  sortBy: 'stock-asc' | 'stock-desc' | 'name-asc' | 'name-desc' = 'stock-asc';

  // Pagination state
  currentPage = 0;
  pageSize = 10;

  get displayedInventory(): InventoryRecord[] {
    let list = [...this.inventoryList];
    if (this.showLowStockOnly) {
      list = list.filter(i => i.isLowStock);
    }
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase().trim();
      list = list.filter(i =>
        (i.productName && i.productName.toLowerCase().includes(q)) ||
        (i.sku && i.sku.toLowerCase().includes(q)) ||
        (i.brandName && i.brandName.toLowerCase().includes(q))
      );
    }
    switch (this.sortBy) {
      case 'stock-asc':
        return list.sort((a, b) => a.availableStock - b.availableStock);
      case 'stock-desc':
        return list.sort((a, b) => b.availableStock - a.availableStock);
      case 'name-asc':
        return list.sort((a, b) => (a.productName || '').localeCompare(b.productName || ''));
      case 'name-desc':
        return list.sort((a, b) => (b.productName || '').localeCompare(a.productName || ''));
      default:
        return list;
    }
  }

  get paginatedInventory(): InventoryRecord[] {
    const start = this.currentPage * this.pageSize;
    return this.displayedInventory.slice(start, start + this.pageSize);
  }

  get totalElements(): number {
    return this.displayedInventory.length;
  }

  onPageChange(page: number): void {
    this.currentPage = page;
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.currentPage = 0;
  }

  // Adjust Modal
  showModal = false;
  selectedItem: InventoryRecord | null = null;
  adjustmentQty = 0;
  adjustReason = 'Stock shipment arrival';
  modalErrorMessage = '';

  constructor(
    private http: HttpClient,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.fetchInventory();
  }

  fetchInventory(): void {
    this.isLoading = true;
    this.http.get<any>(`${environment.apiUrl}/admin/inventory`).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data && res.data.length > 0) {
          this.inventoryList = res.data.map((item: any) => ({
            id: item.id,
            productId: item.productId,
            productName: item.productName,
            sku: item.sku || 'SKU-000',
            brandName: item.brandName || 'Petzlife',
            availableStock: item.availableStock,
            reservedStock: item.reservedStock || 0,
            minThreshold: item.minThreshold || 5,
            isLowStock: item.isLowStock !== undefined ? item.isLowStock : item.availableStock <= (item.minThreshold || 5)
          }));
          this.checkAdjustActionParam();
        } else {
          this.fallbackFetchLowStock();
        }
      },
      error: () => {
        this.fallbackFetchLowStock();
      }
    });
  }

  private fallbackFetchLowStock(): void {
    this.http.get<any>(`${environment.apiUrl}/admin/analytics/low-stock`).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data && res.data.length > 0) {
          this.inventoryList = res.data.map((item: any) => ({
            id: item.inventoryId || item.id,
            productId: item.productId,
            productName: item.productName,
            sku: item.sku || 'SKU-000',
            brandName: item.brandName || 'Petzlife',
            availableStock: item.availableStock,
            reservedStock: item.reservedStock || 0,
            minThreshold: item.minThreshold || 5,
            isLowStock: item.availableStock <= (item.minThreshold || 5)
          }));
          this.checkAdjustActionParam();
        } else {
          this.inventoryList = [];
        }
      },
      error: () => {
        this.isLoading = false;
        this.inventoryList = [];
      }
    });
  }

  private checkAdjustActionParam(): void {
    this.route.queryParams.subscribe(params => {
      if (params['action'] === 'adjust' && this.inventoryList.length > 0 && !this.showModal) {
        this.openAdjustModal(this.inventoryList[0]);
      }
    });
  }

  openAdjustModal(item: InventoryRecord): void {
    this.selectedItem = item;
    this.adjustmentQty = 10;
    this.adjustReason = 'Stock shipment arrival at Kuravankonam';
    this.modalErrorMessage = '';
    this.showModal = true;
  }

  saveAdjustment(): void {
    if (!this.selectedItem || this.adjustmentQty === 0) return;

    const payload = {
      productId: this.selectedItem.productId,
      variantId: null,
      quantityAdjustment: this.adjustmentQty,
      reason: this.adjustReason
    };

    this.http.post<any>(`${environment.apiUrl}/admin/inventory/adjust`, payload).subscribe({
      next: res => {
        if (res?.success) {
          this.showModal = false;
          this.fetchInventory();
        }
      },
      error: err => {
        this.modalErrorMessage = err.error?.message || 'Failed to adjust stock';
      }
    });
  }
}
