import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { InvoiceService, InvoiceDto } from '../../core/services/invoice.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-invoice',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './invoice.component.html',
  styleUrls: ['./invoice.component.scss']
})
export class InvoiceComponent implements OnInit {
  readonly shopAddress = environment.shopAddress;
  orderNumber = '';
  invoice: InvoiceDto | null = null;
  isLoading = true;
  isDownloadingPdf = false;
  errorMessage = '';

  constructor(
    private route: ActivatedRoute,
    private invoiceService: InvoiceService
  ) {}

  ngOnInit(): void {
    this.orderNumber = this.route.snapshot.paramMap.get('orderNumber') || '';
    if (this.orderNumber) {
      this.loadInvoice(this.orderNumber);
    } else {
      this.isLoading = false;
      this.errorMessage = 'No order number provided.';
    }
  }

  loadInvoice(orderNumber: string): void {
    this.isLoading = true;
    this.errorMessage = '';

    this.invoiceService.getInvoice(orderNumber).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data) {
          this.invoice = res.data;
        } else {
          this.errorMessage = 'Invoice not found for this order.';
        }
      },
      error: err => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Failed to retrieve invoice. Please check the order number.';
      }
    });
  }

  downloadPdf(): void {
    if (!this.orderNumber || this.isDownloadingPdf) return;
    this.isDownloadingPdf = true;

    this.invoiceService.downloadInvoicePdf(this.orderNumber).subscribe({
      next: (blob: Blob) => {
        this.isDownloadingPdf = false;
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        const filename = this.invoice?.invoiceNumber
          ? `Invoice-${this.invoice.invoiceNumber}.pdf`
          : `Invoice-${this.orderNumber}.pdf`;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
      },
      error: err => {
        this.isDownloadingPdf = false;
        this.errorMessage = err.error?.message || 'Failed to download official invoice PDF. Please ensure you are logged in.';
      }
    });
  }

  printInvoice(): void {
    window.print();
  }
}
