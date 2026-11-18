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
  errorMessage = '';

  constructor(private analyticsService: AdminAnalyticsService) {}

  ngOnInit(): void {
    this.fetchAnalytics();
  }

  fetchAnalytics(): void {
    this.isLoading = true;
    this.errorMessage = '';

    this.analyticsService.getSummary().subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.summary = res.data;
        } else {
          this.summary = null;
        }
      },
      error: (err) => {
        this.summary = null;
        this.errorMessage = err?.error?.message || 'Unable to connect to analytics service. Please verify backend connection.';
      }
    });

    this.analyticsService.getLowStockAlerts().subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.lowStockAlerts = res.data;
        } else {
          this.lowStockAlerts = [];
        }
      },
      error: () => {
        this.lowStockAlerts = [];
      }
    });

    this.analyticsService.getTopProducts().subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data) {
          this.topProducts = res.data;
        } else {
          this.topProducts = [];
        }
      },
      error: () => {
        this.isLoading = false;
        this.topProducts = [];
      }
    });
  }

  getStatusKeys(): string[] {
    return this.summary?.ordersByStatus ? Object.keys(this.summary.ordersByStatus) : [];
  }

  getPaymentKeys(): string[] {
    return this.summary?.paymentMethodBreakdown ? Object.keys(this.summary.paymentMethodBreakdown) : [];
  }
}
