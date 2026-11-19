import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { BackButtonComponent } from '../../components/back-button/back-button.component';
import { PaginationComponent } from '../../components/pagination/pagination.component';

export interface CustomerRecord {
  id: number;
  userId?: number;
  email?: string;
  phone: string;
  firstName: string;
  lastName?: string;
  isGuest: boolean;
  addresses?: any[];
}

@Component({
  selector: 'app-customers',
  standalone: true,
  imports: [CommonModule, FormsModule, BackButtonComponent, PaginationComponent],
  templateUrl: './customers.component.html',
  styleUrls: ['./customers.component.scss']
})
export class CustomersComponent implements OnInit {
  customers: CustomerRecord[] = [];
  isLoading = false;
  searchQuery = '';

  // Pagination state
  currentPage = 0;
  pageSize = 10;
  totalElements = 0;

  get paginatedCustomers(): CustomerRecord[] {
    const start = this.currentPage * this.pageSize;
    return this.customers.slice(start, start + this.pageSize);
  }

  onPageChange(page: number): void {
    this.currentPage = page;
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.currentPage = 0;
  }

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchCustomers();
  }

  fetchCustomers(): void {
    this.isLoading = true;
    this.currentPage = 0;
    let url = `${environment.apiUrl}/admin/customers?page=0&size=100`;
    if (this.searchQuery.trim()) {
      url += `&query=${encodeURIComponent(this.searchQuery.trim())}`;
    }

    this.http.get<any>(url).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data?.content) {
          this.customers = res.data.content;
          this.totalElements = res.data.totalElements ?? res.data.content.length;
        } else {
          this.customers = [];
          this.totalElements = 0;
        }
      },
      error: () => {
        this.isLoading = false;
        this.customers = [];
        this.totalElements = 0;
      }
    });
  }

  toggleStatus(c: CustomerRecord): void {
    this.http.patch<any>(`${environment.apiUrl}/admin/customers/${c.id}/status`, {}).subscribe({
      next: res => {
        if (res?.success) {
          alert('Status updated successfully');
        }
      }
    });
  }
}
