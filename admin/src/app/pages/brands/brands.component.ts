import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface BrandItem {
  id: number;
  name: string;
  slug: string;
  description?: string;
  websiteUrl?: string;
  isActive: boolean;
}

@Component({
  selector: 'app-brands',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './brands.component.html',
  styleUrls: ['./brands.component.scss']
})
export class BrandsComponent implements OnInit {
  brands: BrandItem[] = [];
  isLoading = false;

  showModal = false;
  brandForm = {
    name: '',
    description: '',
    websiteUrl: ''
  };
  modalErrorMessage = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchBrands();
  }

  fetchBrands(): void {
    this.isLoading = true;
    this.http.get<any>(`${environment.apiUrl}/brands`).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data) {
          this.brands = res.data;
        }
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  toggleStatus(b: BrandItem): void {
    this.http.patch<any>(`${environment.apiUrl}/admin/brands/${b.id}/status`, {}).subscribe({
      next: res => {
        if (res?.success) {
          b.isActive = !b.isActive;
        }
      }
    });
  }

  openCreateModal(): void {
    this.brandForm = { name: '', description: '', websiteUrl: '' };
    this.modalErrorMessage = '';
    this.showModal = true;
  }

  saveBrand(): void {
    if (!this.brandForm.name.trim()) {
      this.modalErrorMessage = 'Brand name is required';
      return;
    }

    this.http.post<any>(`${environment.apiUrl}/admin/brands`, this.brandForm).subscribe({
      next: res => {
        if (res?.success) {
          this.showModal = false;
          this.fetchBrands();
        }
      },
      error: err => {
        this.modalErrorMessage = err.error?.message || 'Failed to create brand';
      }
    });
  }
}
