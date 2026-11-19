import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-pagination',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="pagination-container" *ngIf="totalElements > 0">
      <div class="pagination-info">
        Showing <strong>{{ startRecord }}</strong> to <strong>{{ endRecord }}</strong> of <strong>{{ totalElements }}</strong> entries
      </div>

      <div class="pagination-controls">
        <div class="page-size-selector">
          <label>Rows per page:</label>
          <select [(ngModel)]="pageSize" (change)="onPageSizeChange()">
            <option *ngFor="let opt of pageSizeOptions" [value]="opt">{{ opt }}</option>
          </select>
        </div>

        <div class="nav-buttons">
          <button
            type="button"
            class="btn-page"
            [disabled]="currentPage === 0"
            (click)="setPage(currentPage - 1)"
            aria-label="Previous Page"
          >
            ‹ Prev
          </button>

          <span class="page-indicator">
            Page <strong>{{ currentPage + 1 }}</strong> of <strong>{{ totalPages }}</strong>
          </span>

          <button
            type="button"
            class="btn-page"
            [disabled]="currentPage >= totalPages - 1"
            (click)="setPage(currentPage + 1)"
            aria-label="Next Page"
          >
            Next ›
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .pagination-container {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 1rem;
      padding: 0.85rem 1.25rem;
      background: #FFFFFF;
      border-top: 1px solid #E2E8F0;
      font-size: 0.85rem;
      color: #475569;
    }

    .pagination-controls {
      display: flex;
      align-items: center;
      gap: 1.5rem;
      flex-wrap: wrap;
    }

    .page-size-selector {
      display: flex;
      align-items: center;
      gap: 0.5rem;

      select {
        padding: 0.25rem 0.5rem;
        border: 1px solid #CBD5E1;
        border-radius: 4px;
        background: #FFFFFF;
        font-size: 0.85rem;
        color: #1E293B;
        cursor: pointer;
      }
    }

    .nav-buttons {
      display: flex;
      align-items: center;
      gap: 0.65rem;

      .btn-page {
        padding: 0.35rem 0.75rem;
        border: 1px solid #CBD5E1;
        border-radius: 4px;
        background: #FFFFFF;
        font-size: 0.82rem;
        font-weight: 600;
        color: #1E293B;
        cursor: pointer;
        transition: background 0.15s, border-color 0.15s;
        min-height: 36px;
        min-width: 36px;

        &:hover:not(:disabled) {
          background: #F1F5F9;
          border-color: #94A3B8;
        }

        &:disabled {
          opacity: 0.45;
          cursor: not-allowed;
        }
      }

      .page-indicator {
        font-size: 0.85rem;
      }
    }

    @media (max-width: 640px) {
      .pagination-container {
        flex-direction: column;
        align-items: flex-start;
      }
    }
  `]
})
export class PaginationComponent {
  @Input() currentPage: number = 0;
  @Input() pageSize: number = 10;
  @Input() totalElements: number = 0;
  @Input() pageSizeOptions: number[] = [10, 25, 50];

  @Output() pageChange = new EventEmitter<number>();
  @Output() pageSizeChange = new EventEmitter<number>();

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.totalElements / this.pageSize));
  }

  get startRecord(): number {
    if (this.totalElements === 0) return 0;
    return this.currentPage * this.pageSize + 1;
  }

  get endRecord(): number {
    return Math.min((this.currentPage + 1) * this.pageSize, this.totalElements);
  }

  setPage(page: number): void {
    if (page >= 0 && page < this.totalPages && page !== this.currentPage) {
      this.pageChange.emit(page);
    }
  }

  onPageSizeChange(): void {
    this.pageSizeChange.emit(Number(this.pageSize));
  }
}
