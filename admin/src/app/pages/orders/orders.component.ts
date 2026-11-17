import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminOrderService, AdminOrderSummary, AdminOrderDetail } from '../../core/services/admin-order.service';

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

  statusTabs = ['ALL', 'CONFIRMED', 'PROCESSING', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED'];

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
          this.loadDemoOrders();
        }
      },
      error: () => {
        this.isLoading = false;
        this.loadDemoOrders();
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
          this.selectedOrder = this.buildDemoDetail(order);
        }
      },
      error: () => {
        this.selectedOrder = this.buildDemoDetail(order);
      }
    });
  }

  closeDetail(): void {
    this.selectedOrder = null;
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

  private loadDemoOrders(): void {
    this.orders = [
      {
        id: 1,
        orderNumber: 'ORD-20261114-K8F29A',
        customerName: 'Suresh Kumar',
        customerPhone: '+91 94471 23456',
        orderStatus: 'CONFIRMED',
        paymentStatus: 'PENDING',
        paymentMethod: 'COD',
        finalPayableAmount: 1150.00,
        totalItems: 1,
        deliverySlot: 'Evening Slot (05:00 PM - 08:30 PM)',
        shopDistanceKm: 1.12,
        createdAt: new Date().toISOString()
      },
      {
        id: 2,
        orderNumber: 'ORD-20261114-M4B18P',
        customerName: 'Anitha Varma',
        customerPhone: '+91 98470 56789',
        orderStatus: 'PROCESSING',
        paymentStatus: 'COMPLETED',
        paymentMethod: 'RAZORPAY',
        finalPayableAmount: 2380.00,
        totalItems: 3,
        deliverySlot: 'Morning Slot (09:00 AM - 01:00 PM)',
        shopDistanceKm: 2.85,
        createdAt: new Date(Date.now() - 3600000).toISOString()
      },
      {
        id: 3,
        orderNumber: 'ORD-20261113-X9L02R',
        customerName: 'Deepak Nair',
        customerPhone: '+91 94951 88990',
        orderStatus: 'DELIVERED',
        paymentStatus: 'COMPLETED',
        paymentMethod: 'COD',
        finalPayableAmount: 680.00,
        totalItems: 1,
        deliverySlot: 'Afternoon Slot (01:00 PM - 05:00 PM)',
        shopDistanceKm: 0.85,
        createdAt: new Date(Date.now() - 86400000).toISOString()
      }
    ];

    if (this.activeTab !== 'ALL') {
      this.orders = this.orders.filter(o => o.orderStatus === this.activeTab);
    }
  }

  private buildDemoDetail(summary: AdminOrderSummary): AdminOrderDetail {
    return {
      id: summary.id,
      orderNumber: summary.orderNumber,
      customerName: summary.customerName,
      customerEmail: 'customer@example.com',
      customerPhone: summary.customerPhone,
      orderStatus: summary.orderStatus,
      paymentStatus: summary.paymentStatus,
      paymentMethod: summary.paymentMethod,
      totalAmount: summary.finalPayableAmount,
      deliveryCharge: 0,
      finalPayableAmount: summary.finalPayableAmount,
      deliveryAddressJson: JSON.stringify({
        recipientName: summary.customerName,
        phone: summary.customerPhone,
        addressLine1: 'TC 4/1202, Near Palace Gate',
        city: 'Thiruvananthapuram',
        state: 'Kerala',
        postalCode: '695004'
      }),
      shopDistanceKm: summary.shopDistanceKm,
      deliverySlot: summary.deliverySlot,
      items: [
        {
          id: 101,
          productName: 'Arden Grange Adult Dog Fresh Chicken & Rice',
          brandName: 'Arden Grange',
          sku: 'AG-DOG-CHK-2KG',
          quantity: summary.totalItems,
          unitPrice: summary.finalPayableAmount / summary.totalItems,
          totalPrice: summary.finalPayableAmount,
          returnPolicySnapshot: '{"returnAllowed":true,"returnWindowDays":10,"customConditions":"Intact seal required"}',
          refundPolicySnapshot: '{"refundAllowed":true,"refundMethod":"ORIGINAL_PAYMENT"}'
        }
      ],
      statusHistory: [
        { newStatus: summary.orderStatus, changedBy: 'STORE_DISPATCH', createdAt: summary.createdAt }
      ],
      createdAt: summary.createdAt
    };
  }
}
