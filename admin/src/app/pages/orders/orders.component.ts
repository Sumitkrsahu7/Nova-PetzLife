import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminOrderService, AdminOrderSummary, AdminOrderDetail, AdminOrderItem } from '../../core/services/admin-order.service';
import { BackButtonComponent } from '../../components/back-button/back-button.component';
import { PaginationComponent } from '../../components/pagination/pagination.component';

export interface ReturnPolicyParsed {
  isJson: boolean;
  returnAllowed: boolean;
  returnWindowDays: number;
  originalPackagingRequired: boolean;
  unusedConditionRequired: boolean;
  customConditions?: string;
  rawText?: string;
}

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, BackButtonComponent, PaginationComponent],
  templateUrl: './orders.component.html',
  styleUrls: ['./orders.component.scss']
})
export class OrdersComponent implements OnInit {
  orders: AdminOrderSummary[] = [];
  selectedOrder: AdminOrderDetail | null = null;
  isLoading = false;
  searchQuery = '';
  activeTab = 'ALL';

  statusTabs = ['ALL', 'PENDING_PAYMENT', 'CONFIRMED', 'PACKED', 'DISPATCHED', 'DELIVERED', 'CANCELLED'];

  // Pagination state
  currentPage = 0;
  pageSize = 10;
  totalElements = 0;

  // Out of Stock Item Cancellation Modal State
  showCancelItemModal = false;
  selectedCancelItem: AdminOrderItem | null = null;
  cancelItemReason = 'Item currently out of stock at store';
  isCancellingItem = false;
  cancelItemError = '';

  constructor(private adminOrderService: AdminOrderService) { }

  ngOnInit(): void {
    this.fetchOrders();
  }

  fetchOrders(): void {
    this.isLoading = true;
    this.adminOrderService.getOrders(this.activeTab, this.searchQuery, this.currentPage, this.pageSize).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data?.content) {
          this.orders = res.data.content;
          this.totalElements = res.data.totalElements ?? res.data.content.length;
        } else {
          this.orders = [];
          this.totalElements = 0;
        }
      },
      error: () => {
        this.isLoading = false;
        this.orders = [];
        this.totalElements = 0;
      }
    });
  }

  onPageChange(page: number): void {
    this.currentPage = page;
    this.fetchOrders();
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.currentPage = 0;
    this.fetchOrders();
  }

  setTab(tab: string): void {
    this.activeTab = tab;
    this.currentPage = 0;
    this.fetchOrders();
  }

  onSearch(): void {
    this.currentPage = 0;
    this.fetchOrders();
  }

  parsePolicy(snapshot: string | undefined): ReturnPolicyParsed {
    if (!snapshot) {
      return {
        isJson: false,
        returnAllowed: false,
        returnWindowDays: 0,
        originalPackagingRequired: false,
        unusedConditionRequired: false,
        rawText: 'Standard return policy applies'
      };
    }
    try {
      const parsed = JSON.parse(snapshot);
      if (typeof parsed === 'object' && parsed !== null) {
        return {
          isJson: true,
          returnAllowed: !!parsed.returnAllowed,
          returnWindowDays: Number(parsed.returnWindowDays ?? 0),
          originalPackagingRequired: !!parsed.originalPackagingRequired,
          unusedConditionRequired: !!parsed.unusedConditionRequired,
          customConditions: parsed.customConditions || ''
        };
      }
    } catch {
      // not a json string
    }
    return {
      isJson: false,
      returnAllowed: true,
      returnWindowDays: 7,
      originalPackagingRequired: false,
      unusedConditionRequired: false,
      rawText: snapshot
    };
  }

  viewOrder(order: AdminOrderSummary): void {
    this.adminOrderService.getOrderDetail(order.orderNumber).subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.selectedOrder = res.data;
        } else {
          alert('Unable to retrieve full details for this order.');
        }
      },
      error: () => {
        alert('Failed to connect to server for order details.');
      }
    });
  }

  closeDetail(): void {
    this.selectedOrder = null;
  }

  openCancelItemModal(item: any): void {
    this.selectedCancelItem = item;
    this.cancelItemReason = 'Item currently out of stock at store';
    this.cancelItemError = '';
    this.showCancelItemModal = true;
  }

  confirmCancelItem(): void {
    if (!this.selectedOrder || !this.selectedCancelItem) return;
    this.isCancellingItem = true;
    this.cancelItemError = '';

    this.adminOrderService.cancelOrderItem(
      this.selectedOrder.id,
      this.selectedCancelItem.id,
      this.cancelItemReason
    ).subscribe({
      next: (res) => {
        this.isCancellingItem = false;
        this.showCancelItemModal = false;
        if (res?.success && res.data) {
          this.selectedOrder = res.data;
          this.fetchOrders();
          alert(`Item '${this.selectedCancelItem?.productName}' cancelled. Stock restored and refund initiated if paid.`);
        }
      },
      error: (err) => {
        this.isCancellingItem = false;
        this.cancelItemError = err.error?.message || 'Failed to cancel order item';
      }
    });
  }

  isDownloadingInvoice = false;
  invoiceDownloadError = '';

  printInvoice(orderNumber: string): void {
    if (!orderNumber) return;
    this.isDownloadingInvoice = true;
    this.invoiceDownloadError = '';

    this.adminOrderService.downloadInvoicePdf(orderNumber).subscribe({
      next: (blob: Blob) => {
        this.isDownloadingInvoice = false;
        const blobUrl = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = blobUrl;
        link.download = `Invoice-${orderNumber}.pdf`;
        link.target = '_blank';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        setTimeout(() => window.URL.revokeObjectURL(blobUrl), 2000);
      },
      error: (err) => {
        this.isDownloadingInvoice = false;
        this.invoiceDownloadError = err.error?.message || 'Failed to download official invoice PDF';
        console.error('Invoice download error:', err);
      }
    });
  }

  deliveryOtpInput = '';
  showOtpModal = false;
  otpOrderId: number | null = null;
  otpErrorMessage = '';

  updateStatus(orderId: number, newStatus: string): void {
    if (newStatus === 'DELIVERED') {
      this.confirmDeliveryWithOtp(orderId);
      return;
    }

    this.adminOrderService.updateOrderStatus(orderId, newStatus, 'Admin console update').subscribe({
      next: () => {
        this.fetchOrders();
        if (this.selectedOrder && this.selectedOrder.id === orderId) {
          this.selectedOrder.orderStatus = newStatus;
        }
      },
      error: (err) => {
        alert(err.error?.message || 'Failed to update order status');
      }
    });
  }

  confirmDeliveryWithOtp(orderId: number): void {
    this.otpOrderId = orderId;
    this.deliveryOtpInput = '';
    this.otpErrorMessage = '';
    this.showOtpModal = true;
  }

  submitDeliveryOtp(): void {
    if (!this.otpOrderId || !this.deliveryOtpInput.trim()) {
      this.otpErrorMessage = 'Please enter the 6-digit delivery OTP code.';
      return;
    }

    this.adminOrderService.verifyDeliveryOtp(this.otpOrderId, this.deliveryOtpInput.trim()).subscribe({
      next: res => {
        this.showOtpModal = false;
        this.fetchOrders();
        if (this.selectedOrder && this.selectedOrder.id === this.otpOrderId) {
          this.selectedOrder.orderStatus = 'DELIVERED';
        }
      },
      error: err => {
        this.otpErrorMessage = err.error?.message || 'Invalid delivery OTP code. Please verify with customer.';
      }
    });
  }
}
