import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminCouponService, AdminCoupon, CreateCouponPayload } from '../../core/services/admin-coupon.service';

@Component({
  selector: 'app-coupons',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './coupons.component.html',
  styleUrls: ['./coupons.component.scss']
})
export class CouponsComponent implements OnInit {
  coupons: AdminCoupon[] = [];
  isLoading = false;
  isSaving = false;
  showCreateModal = false;
  successMessage = '';
  errorMessage = '';

  newCoupon: CreateCouponPayload = {
    code: '',
    description: '',
    discountType: 'PERCENTAGE',
    discountValue: 10,
    minOrderValue: 500,
    maxDiscountAmount: 200,
    validFrom: new Date().toISOString().split('T')[0],
    validUntil: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
    isActive: true,
    totalUsageLimit: 100,
    perCustomerUsageLimit: 1
  };

  constructor(private adminCouponService: AdminCouponService) {}

  ngOnInit(): void {
    this.fetchCoupons();
  }

  fetchCoupons(): void {
    this.isLoading = true;
    this.adminCouponService.getCoupons().subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data?.content) {
          this.coupons = res.data.content;
        } else {
          this.coupons = [];
        }
      },
      error: () => {
        this.isLoading = false;
        this.coupons = [];
      }
    });
  }

  openCreateModal(): void {
    this.newCoupon = {
      code: '',
      description: '',
      discountType: 'PERCENTAGE',
      discountValue: 10,
      minOrderValue: 500,
      maxDiscountAmount: 200,
      validFrom: new Date().toISOString().split('T')[0],
      validUntil: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
      isActive: true,
      totalUsageLimit: 100,
      perCustomerUsageLimit: 1
    };
    this.showCreateModal = true;
    this.errorMessage = '';
    this.successMessage = '';
  }

  closeModal(): void {
    this.showCreateModal = false;
  }

  saveCoupon(): void {
    if (!this.newCoupon.code.trim() || !this.newCoupon.description.trim()) {
      this.errorMessage = 'Coupon code and description are required.';
      return;
    }

    this.newCoupon.code = this.newCoupon.code.trim().toUpperCase();
    this.isSaving = true;
    this.errorMessage = '';

    this.adminCouponService.createCoupon(this.newCoupon).subscribe({
      next: res => {
        this.isSaving = false;
        this.showCreateModal = false;
        this.successMessage = `Coupon "${this.newCoupon.code}" created successfully!`;
        this.fetchCoupons();
        setTimeout(() => this.successMessage = '', 4000);
      },
      error: err => {
        this.isSaving = false;
        this.errorMessage = err.error?.message || 'Failed to create coupon code.';
      }
    });
  }

  toggleActive(coupon: AdminCoupon): void {
    this.adminCouponService.toggleCoupon(coupon.id).subscribe({
      next: res => {
        coupon.isActive = !coupon.isActive;
        this.successMessage = `Coupon "${coupon.code}" status updated to ${coupon.isActive ? 'ACTIVE' : 'INACTIVE'}.`;
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: err => {
        this.errorMessage = err.error?.message || 'Failed to toggle coupon status.';
      }
    });
  }

  deleteCoupon(coupon: AdminCoupon): void {
    if (!confirm(`Are you sure you want to permanently delete coupon "${coupon.code}"?`)) {
      return;
    }

    this.adminCouponService.deleteCoupon(coupon.id).subscribe({
      next: () => {
        this.successMessage = `Coupon "${coupon.code}" deleted.`;
        this.fetchCoupons();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: err => {
        this.errorMessage = err.error?.message || 'Failed to delete coupon.';
      }
    });
  }
}
