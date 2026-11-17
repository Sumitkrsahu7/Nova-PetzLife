import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminAuditService, AuditLogItem } from '../../core/services/admin-audit.service';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './audit.component.html',
  styleUrls: ['./audit.component.scss']
})
export class AuditComponent implements OnInit {
  logs: AuditLogItem[] = [];
  isLoading: boolean = true;

  // Filters
  selectedEntity: string = 'ALL';
  selectedAction: string = 'ALL';
  currentPage: number = 0;
  totalPages: number = 1;
  totalElements: number = 0;

  entities = ['ALL', 'Order', 'Product', 'Inventory', 'Refund', 'Review', 'Coupon', 'ShopLocation'];
  actions = ['ALL', 'ORDER_STATUS_CHANGED', 'STOCK_ADJUSTED', 'REFUND_PROCESSED', 'REVIEW_MODERATED', 'PRICE_CHANGE'];

  // Detail inspection modal
  selectedLog: AuditLogItem | null = null;

  constructor(private auditService: AdminAuditService) {}

  ngOnInit(): void {
    this.loadLogs();
  }

  loadLogs(): void {
    this.isLoading = true;
    this.auditService.getAuditLogs(this.selectedEntity, this.selectedAction, this.currentPage, 20).subscribe({
      next: (res) => {
        this.logs = res.content || [];
        this.totalPages = res.totalPages || 1;
        this.totalElements = res.totalElements || 0;
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        console.error('Failed to load audit logs', err);
      }
    });
  }

  onFilterChange(): void {
    this.currentPage = 0;
    this.loadLogs();
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.loadLogs();
    }
  }

  prevPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadLogs();
    }
  }

  inspectLog(log: AuditLogItem): void {
    this.selectedLog = log;
  }

  closeModal(): void {
    this.selectedLog = null;
  }
}
