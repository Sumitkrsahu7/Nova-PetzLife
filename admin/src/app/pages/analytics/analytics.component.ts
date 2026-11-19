import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AdminAnalyticsService, AnalyticsSummary, LowStockAlert, TopProduct } from '../../core/services/admin-analytics.service';
import { BackButtonComponent } from '../../components/back-button/back-button.component';
import { PaginationComponent } from '../../components/pagination/pagination.component';

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [CommonModule, RouterModule, BackButtonComponent, PaginationComponent],
  templateUrl: './analytics.component.html',
  styleUrls: ['./analytics.component.scss']
})
export class AnalyticsComponent implements OnInit {
  summary: AnalyticsSummary | null = null;
  lowStockAlerts: LowStockAlert[] = [];
  topProducts: TopProduct[] = [];
  isLoading = false;
  errorMessage = '';

  // Low stock pagination
  alertPage = 0;
  alertPageSize = 10;

  get paginatedAlerts(): LowStockAlert[] {
    const start = this.alertPage * this.alertPageSize;
    return this.lowStockAlerts.slice(start, start + this.alertPageSize);
  }

  get totalAlertElements(): number {
    return this.lowStockAlerts.length;
  }

  onAlertPageChange(page: number): void {
    this.alertPage = page;
  }

  onAlertPageSizeChange(size: number): void {
    this.alertPageSize = size;
    this.alertPage = 0;
  }

  // Top products pagination
  topPage = 0;
  topPageSize = 10;

  get paginatedTopProducts(): TopProduct[] {
    const start = this.topPage * this.topPageSize;
    return this.topProducts.slice(start, start + this.topPageSize);
  }

  get totalTopElements(): number {
    return this.topProducts.length;
  }

  onTopPageChange(page: number): void {
    this.topPage = page;
  }

  onTopPageSizeChange(size: number): void {
    this.topPageSize = size;
    this.topPage = 0;
  }

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
