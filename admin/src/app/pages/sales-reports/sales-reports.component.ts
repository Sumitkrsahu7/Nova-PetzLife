import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface SalesReportOrder {
  orderId: number;
  orderNumber: string;
  orderDate: string;
  formattedDate: string;
  customerName: string;
  customerEmail: string;
  customerPhone: string;
  orderStatus: string;
  paymentStatus: string;
  paymentMethod: string;
  itemsCount: number;
  subtotal: number;
  discountAmount: number;
  shippingAmount: number;
  taxAmount: number;
  grandTotal: number;
  refundAmount: number;
  netAmount: number;
  shippingCity: string;
  shippingPincode: string;
  itemsSummary: string;
}

export interface SalesReportSummary {
  period: string;
  queryDate: string;
  startInstant: string;
  endInstant: string;
  formattedDateRange: string;
  totalOrders: number;
  successfulOrders: number;
  pendingOrders: number;
  cancelledOrders: number;
  refundedOrders: number;
  failedOrders: number;
  itemSubtotal: number;
  customerReceipts: number;
  grossSales?: number;
  totalDiscounts: number;
  totalShipping: number;
  totalTax: number;
  totalRefunds: number;
  netReceipts: number;
  netSales?: number;
  totalUnitsSold: number;
  averageOrderValue: number;
  orders: SalesReportOrder[];
  unattributableOrdersCount?: number;
  unattributableAmount?: number;
}

@Component({
  selector: 'app-sales-reports',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './sales-reports.component.html',
  styleUrls: ['./sales-reports.component.scss']
})
export class SalesReportsComponent implements OnInit {
  period: 'WEEKLY' | 'MONTHLY' | 'YEARLY' = 'WEEKLY';
  selectedDate: string = new Date().toISOString().split('T')[0];
  report: SalesReportSummary | null = null;
  isLoading = false;
  errorMessage = '';
  isExportingCsv = false;
  isExportingPdf = false;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadReport();
  }

  setPeriod(p: 'WEEKLY' | 'MONTHLY' | 'YEARLY'): void {
    this.period = p;
    this.loadReport();
  }

  onDateChange(): void {
    this.loadReport();
  }

  loadReport(): void {
    this.isLoading = true;
    this.errorMessage = '';

    const url = `${environment.apiUrl}/admin/reports/sales/summary?period=${this.period}&date=${this.selectedDate}`;
    this.http.get<any>(url).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data) {
          this.report = res.data;
        } else {
          this.errorMessage = res?.message || 'Failed to load sales report';
        }
      },
      error: err => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Failed to fetch sales report from server';
      }
    });
  }

  exportCsv(): void {
    if (this.isExportingCsv) return;
    this.isExportingCsv = true;

    const url = `${environment.apiUrl}/admin/reports/sales/export/csv?period=${this.period}&date=${this.selectedDate}`;
    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob: Blob) => {
        this.isExportingCsv = false;
        const downloadUrl = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = downloadUrl;
        a.download = `SalesReport-${this.period}-${this.selectedDate}.csv`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(downloadUrl);
      },
      error: err => {
        this.isExportingCsv = false;
        this.errorMessage = err.error?.message || 'Failed to download CSV export.';
      }
    });
  }

  exportPdf(): void {
    if (this.isExportingPdf) return;
    this.isExportingPdf = true;

    const url = `${environment.apiUrl}/admin/reports/sales/export/pdf?period=${this.period}&date=${this.selectedDate}`;
    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob: Blob) => {
        this.isExportingPdf = false;
        const downloadUrl = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = downloadUrl;
        a.download = `SalesReport-${this.period}-${this.selectedDate}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(downloadUrl);
      },
      error: err => {
        this.isExportingPdf = false;
        this.errorMessage = err.error?.message || 'Failed to download PDF export.';
      }
    });
  }
}
