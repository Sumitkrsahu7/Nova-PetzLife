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
          this.loadDefaultDemo();
        }
      },
      error: () => {
        this.isLoading = false;
        this.loadDefaultDemo();
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

  private loadDefaultDemo(): void {
    this.customers = [
      { id: 1, email: 'anand.k@example.com', phone: '+91 94471 23456', firstName: 'Anand', lastName: 'Kumar', isGuest: false },
      { id: 2, email: 'kavitha.s@example.com', phone: '+91 98470 54321', firstName: 'Kavitha', lastName: 'Suresh', isGuest: false },
      { id: 3, email: 'rahul.menon@example.com', phone: '+91 94473 11223', firstName: 'Rahul', lastName: 'Menon', isGuest: true }
    ];
  }
}
