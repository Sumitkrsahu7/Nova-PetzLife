import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../environments/environment';

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
  imports: [CommonModule, FormsModule],
  templateUrl: './inventory.component.html',
  styleUrls: ['./inventory.component.scss']
})
export class InventoryComponent implements OnInit {
  inventoryList: InventoryRecord[] = [];
  isLoading = false;
  showLowStockOnly = false;

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
          this.loadDefaultDemo();
        }
      },
      error: () => {
        this.isLoading = false;
        this.loadDefaultDemo();
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

  private loadDefaultDemo(): void {
    this.inventoryList = [
      { id: 1, productId: 1, productName: 'Royal Canin Kitten Wet Food 85g', sku: 'RC-KIT-WET', brandName: 'Royal Canin', availableStock: 3, reservedStock: 1, minThreshold: 5, isLowStock: true },
      { id: 2, productId: 2, productName: 'Pedigree Puppy Chicken & Rice 3kg', sku: 'PED-PUP-3KG', brandName: 'Pedigree', availableStock: 2, reservedStock: 0, minThreshold: 5, isLowStock: true },
      { id: 3, productId: 3, productName: 'Cat Bentonite Clumping Litter 10L', sku: 'LIT-BENT-10L', brandName: 'Petzlife', availableStock: 4, reservedStock: 2, minThreshold: 5, isLowStock: true }
    ];
  }
}
