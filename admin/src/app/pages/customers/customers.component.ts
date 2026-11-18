import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

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
  imports: [CommonModule, FormsModule],
  templateUrl: './customers.component.html',
  styleUrls: ['./customers.component.scss']
})
export class CustomersComponent implements OnInit {
  customers: CustomerRecord[] = [];
  isLoading = false;
  searchQuery = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchCustomers();
  }

  fetchCustomers(): void {
    this.isLoading = true;
    let url = `${environment.apiUrl}/admin/customers?page=0&size=50`;
    if (this.searchQuery.trim()) {
      url += `&query=${encodeURIComponent(this.searchQuery.trim())}`;
    }

    this.http.get<any>(url).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data?.content) {
          this.customers = res.data.content;
        } else {
          this.customers = [];
        }
      },
      error: () => {
        this.isLoading = false;
        this.customers = [];
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
