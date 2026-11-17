import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AdminAnalyticsService, AnalyticsSummary, LowStockAlert, TopProduct } from '../../core/services/admin-analytics.service';

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './analytics.component.html',
  styleUrls: ['./analytics.component.scss']
})
export class AnalyticsComponent implements OnInit {
  summary: AnalyticsSummary | null = null;
  lowStockAlerts: LowStockAlert[] = [];
  topProducts: TopProduct[] = [];
  isLoading = false;

  constructor(private analyticsService: AdminAnalyticsService) {}

  ngOnInit(): void {
    this.fetchAnalytics();
  }

  fetchAnalytics(): void {
    this.isLoading = true;

    this.analyticsService.getSummary().subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.summary = res.data;
        } else {
          this.loadDemoSummary();
        }
      },
      error: () => {
        this.loadDemoSummary();
      }
    });

    this.analyticsService.getLowStockAlerts().subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.lowStockAlerts = res.data;
        }
      }
    });

    this.analyticsService.getTopProducts().subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data) {
          this.topProducts = res.data;
        }
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  loadDemoSummary(): void {
    this.summary = {
      totalRevenue: 84650.00,
      totalOrders: 64,
      deliveredOrders: 48,
      cancelledOrders: 2,
      averageOrderValue: 1322.65,
      totalRefunded: 1798.00,
      activeCouponsCount: 2,
      lowStockCount: 4,
      ordersWithin20kmCount: 62,
      ordersByStatus: {
        'DELIVERED': 48,
        'CONFIRMED': 8,
        'OUT_FOR_DELIVERY': 4,
        'PROCESSING': 2,
        'CANCELLED': 2
      },
      paymentMethodBreakdown: {
        'COD': 38,
        'RAZORPAY': 26
      }
    };
  }

  getStatusKeys(): string[] {
    return this.summary?.ordersByStatus ? Object.keys(this.summary.ordersByStatus) : [];
  }

  getPaymentKeys(): string[] {
    return this.summary?.paymentMethodBreakdown ? Object.keys(this.summary.paymentMethodBreakdown) : [];
  }
}
