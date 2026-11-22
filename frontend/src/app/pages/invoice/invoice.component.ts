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

  printInvoice(): void {
    window.print();
  }
}
