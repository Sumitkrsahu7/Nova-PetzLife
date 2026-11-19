import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminOrderService, AdminOrderSummary, AdminOrderDetail, AdminOrderItem } from '../../core/services/admin-order.service';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
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

  // Out of Stock Item Cancellation Modal State
  showCancelItemModal = false;
  selectedCancelItem: AdminOrderItem | null = null;
  cancelItemReason = 'Item currently out of stock at store';
  isCancellingItem = false;
  cancelItemError = '';

  constructor(private adminOrderService: AdminOrderService) {}

  ngOnInit(): void {
    this.fetchOrders();
  }

  fetchOrders(): void {
    this.isLoading = true;
    this.adminOrderService.getOrders(this.activeTab, this.searchQuery).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data?.content) {
          this.orders = res.data.content;
        } else {
          this.orders = [];
        }
      },
      error: () => {
        this.isLoading = false;
        this.orders = [];
      }
    });
  }

  setTab(tab: string): void {
    this.activeTab = tab;
    this.fetchOrders();
  }

  onSearch(): void {
    this.fetchOrders();
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

  printInvoice(orderNumber: string): void {
    window.open(`http://localhost:4200/invoice/${orderNumber}`, '_blank');
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
