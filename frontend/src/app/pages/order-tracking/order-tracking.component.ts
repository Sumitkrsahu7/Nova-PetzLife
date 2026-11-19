import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { OrderService, OrderTracking } from '../../core/services/order.service';
import { ReturnService, ReturnEligibility, ReturnRequestDetail } from '../../core/services/return.service';
import { BackButtonComponent } from '../../components/back-button/back-button.component';

@Component({
  selector: 'app-order-tracking',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, BackButtonComponent],
  templateUrl: './order-tracking.component.html',
  styleUrls: ['./order-tracking.component.scss']
})
export class OrderTrackingComponent implements OnInit {
  orderNumber = '';
  tracking: OrderTracking | null = null;
  isLoading = true;
  errorMessage = '';

  stages = [
    { key: 'CONFIRMED', label: 'Order Confirmed', icon: '📝' },
    { key: 'PACKED', label: 'Packed at Store', icon: '📦' },
    { key: 'DISPATCHED', label: 'Out for Delivery', icon: '🛵' },
    { key: 'DELIVERED', label: 'Delivered', icon: '🎉' }
  ];

  // Cancellation State
  showCancelModal = false;
  cancelReason = 'Changed mind / Ordered by mistake';
  cancelReasons = [
    'Changed mind / Ordered by mistake',
    'Found better price elsewhere',
    'Delivery slot no longer convenient',
    'Ordered incorrect size or brand',
    'Other reason'
  ];
  isCancelling = false;

  // Return & Refund State
  showReturnModal = false;
  isLoadingEligibility = false;
  eligibility: ReturnEligibility | null = null;
  selectedItems: { [orderItemId: number]: { selected: boolean; quantity: number } } = {};
  returnReason = 'Defective or damaged in transit';
  returnReasons = [
    'Defective or damaged in transit',
    'Wrong item or size delivered',
    'Packaging seal was broken or compromised',
    'Pet refused / unopened bag in original packaging'
  ];
  returnComments = '';
  isSubmittingReturn = false;
  returnErrorMessage = '';
  submittedReturn: ReturnRequestDetail | null = null;

  constructor(
    private route: ActivatedRoute,
    private orderService: OrderService,
    private returnService: ReturnService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      this.orderNumber = params.get('orderNumber') || '';
      if (this.orderNumber) {
        this.fetchTracking();
      }
    });
  }

  fetchTracking(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.orderService.trackOrder(this.orderNumber).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data) {
          this.tracking = res.data;
        }
      },
      error: err => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Could not find order with number: ' + this.orderNumber;
      }
    });
  }

  get isCancellable(): boolean {
    if (!this.tracking) return false;
    const status = this.tracking.orderStatus.toUpperCase();
    return status === 'PENDING_PAYMENT' || status === 'CONFIRMED' || status === 'PAID';
  }

  get isPackedOrDispatched(): boolean {
    if (!this.tracking) return false;
    const status = this.tracking.orderStatus.toUpperCase();
    return status === 'PACKED' || status === 'DISPATCHED' || status === 'OUT_FOR_DELIVERY';
  }

  get isDelivered(): boolean {
    return this.tracking?.orderStatus?.toUpperCase() === 'DELIVERED';
  }

  openCancelModal(): void {
    this.showCancelModal = true;
  }

  closeCancelModal(): void {
    this.showCancelModal = false;
  }

  confirmCancelOrder(): void {
    if (!this.orderNumber) return;
    this.isCancelling = true;

    this.returnService.cancelOrder(this.orderNumber, this.cancelReason).subscribe({
      next: () => {
        this.isCancelling = false;
        this.showCancelModal = false;
        this.fetchTracking();
      },
      error: err => {
        this.isCancelling = false;
        alert(err.error?.message || 'Failed to cancel order.');
      }
    });
  }

  openReturnModal(): void {
    this.showReturnModal = true;
    this.isLoadingEligibility = true;
    this.returnErrorMessage = '';
    this.submittedReturn = null;
    this.selectedItems = {};

    this.returnService.getReturnEligibility(this.orderNumber).subscribe({
      next: res => {
        this.isLoadingEligibility = false;
        if (res?.success && res.data) {
          this.eligibility = res.data;
          // Initialize selection state
          for (const it of this.eligibility.items) {
            this.selectedItems[it.orderItemId] = {
              selected: it.isEligible,
              quantity: it.quantity
            };
          }
        }
      },
      error: err => {
        this.isLoadingEligibility = false;
        this.returnErrorMessage = err.error?.message || 'Could not load return eligibility.';
      }
    });
  }

  closeReturnModal(): void {
    this.showReturnModal = false;
  }

  hasSelectedEligibleItems(): boolean {
    if (!this.eligibility) return false;
    return this.eligibility.items.some(it => it.isEligible && this.selectedItems[it.orderItemId]?.selected);
  }

  submitReturnRequest(): void {
    if (!this.eligibility) return;

    const itemsToReturn: { orderItemId: number; quantity: number }[] = [];
    for (const it of this.eligibility.items) {
      if (it.isEligible && this.selectedItems[it.orderItemId]?.selected) {
        itemsToReturn.push({
          orderItemId: it.orderItemId,
          quantity: this.selectedItems[it.orderItemId].quantity || 1
        });
      }
    }

    if (itemsToReturn.length === 0) {
      this.returnErrorMessage = 'Please select at least one eligible item to return.';
      return;
    }

    this.isSubmittingReturn = true;
    this.returnErrorMessage = '';

    this.returnService.submitReturnRequest({
      orderNumber: this.orderNumber,
      items: itemsToReturn,
      reason: this.returnReason,
      customerComments: this.returnComments.trim() || undefined
    }).subscribe({
      next: res => {
        this.isSubmittingReturn = false;
        if (res?.success && res.data) {
          this.submittedReturn = res.data;
          this.fetchTracking();
        }
      },
      error: err => {
        this.isSubmittingReturn = false;
        this.returnErrorMessage = err.error?.message || 'Failed to submit return request.';
      }
    });
  }

  getStageIndex(status: string): number {
    switch (status) {
      case 'CONFIRMED': return 0;
      case 'PROCESSING': return 1;
      case 'OUT_FOR_DELIVERY': return 2;
      case 'DELIVERED': return 3;
      default: return 0;
    }
  }

  isStageCompleted(stageKey: string): boolean {
    if (!this.tracking) return false;
    return this.getStageIndex(this.tracking.orderStatus) >= this.getStageIndex(stageKey);
  }

  isCurrentStage(stageKey: string): boolean {
    if (!this.tracking) return false;
    return this.tracking.orderStatus === stageKey;
  }
}
