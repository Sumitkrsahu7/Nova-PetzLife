import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface CategoryItem {
  id: number;
  name: string;
  slug: string;
  description?: string;
  isActive: boolean;
  displayOrder: number;
  parentId?: number;
}

@Component({
  selector: 'app-categories',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './categories.component.html',
  styleUrls: ['./categories.component.scss']
})
export class CategoriesComponent implements OnInit {
  categories: CategoryItem[] = [];
  isLoading = false;

  showModal = false;
  categoryForm = {
    name: '',
    description: '',
    displayOrder: 0
  };
  modalErrorMessage = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchCategories();
  }

  fetchCategories(): void {
    this.isLoading = true;
    this.http.get<any>(`${environment.apiUrl}/categories/flat`).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data) {
          this.categories = res.data;
        }
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  toggleStatus(c: CategoryItem): void {
    this.http.patch<any>(`${environment.apiUrl}/admin/categories/${c.id}/status`, {}).subscribe({
      next: res => {
        if (res?.success) {
          c.isActive = !c.isActive;
        }
      }
    });
  }

  openCreateModal(): void {
    this.categoryForm = { name: '', description: '', displayOrder: this.categories.length + 1 };
    this.modalErrorMessage = '';
    this.showModal = true;
  }

  saveCategory(): void {
    if (!this.categoryForm.name.trim()) {
      this.modalErrorMessage = 'Category name is required';
      return;
    }

    this.http.post<any>(`${environment.apiUrl}/admin/categories`, this.categoryForm).subscribe({
      next: res => {
        if (res?.success) {
          this.showModal = false;
          this.fetchCategories();
        }
      },
      error: err => {
        this.modalErrorMessage = err.error?.message || 'Failed to create category';
      }
    });
  }
}
