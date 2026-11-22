import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { BackButtonComponent } from '../../components/back-button/back-button.component';
import { PaginationComponent } from '../../components/pagination/pagination.component';

export interface AdminPetSummary {
  id: number;
  petName: string;
  petType: string;
  breed?: string;
  dateOfBirth?: string;
  lifeStage?: string;
  createdAt: string;
  customerId?: number;
  ownerName: string;
  ownerEmail?: string;
  ownerPhone?: string;
  hasFeedback: boolean;
  adminFeedback?: string;
  adminFeedbackUpdatedAt?: string;
}

export interface AdminPetDetail {
  id: number;
  petName: string;
  petType: string;
  breed?: string;
  dateOfBirth?: string;
  gender?: string;
  weightKg?: number;
  lifeStage?: string;
  dietaryPreferences?: string;
  foodSensitivities?: string;
  createdAt: string;
  updatedAt?: string;
  customerId?: number;
  ownerName: string;
  ownerEmail?: string;
  ownerPhone?: string;
  hasFeedback: boolean;
  adminFeedback?: string;
  adminFeedbackUpdatedAt?: string;
}

@Component({
  selector: 'app-admin-pets',
  standalone: true,
  imports: [CommonModule, FormsModule, BackButtonComponent, PaginationComponent],
  templateUrl: './pets.component.html',
  styleUrls: ['./pets.component.scss']
})
export class PetsComponent implements OnInit {
  pets: AdminPetSummary[] = [];
  isLoading = false;
  errorMessage = '';

  // Filters
  searchQuery = '';
  selectedSpecies = 'ALL';
  speciesList = ['ALL', 'Dog', 'Cat', 'Bird', 'Fish', 'Small Animal'];

  // Pagination
  currentPage = 0;
  pageSize = 10;
  totalElements = 0;

  // Pet Details Modal / Drawer
  selectedPetDetail: AdminPetDetail | null = null;
  isLoadingDetail = false;
  feedbackDraft = '';
  isSavingFeedback = false;
  isDeletingFeedback = false;
  feedbackSuccessMessage = '';
  feedbackErrorMessage = '';
  showDeleteConfirm = false;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchPets();
  }

  fetchPets(): void {
    this.isLoading = true;
    this.errorMessage = '';

    let url = `${environment.apiUrl}/admin/pets?page=${this.currentPage}&size=${this.pageSize}`;
    if (this.searchQuery.trim()) {
      url += `&query=${encodeURIComponent(this.searchQuery.trim())}`;
    }
    if (this.selectedSpecies && this.selectedSpecies !== 'ALL') {
      url += `&species=${encodeURIComponent(this.selectedSpecies)}`;
    }

    this.http.get<any>(url).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data?.content) {
          this.pets = res.data.content;
          this.totalElements = res.data.totalElements ?? res.data.content.length;
        } else {
          this.pets = [];
          this.totalElements = 0;
        }
      },
      error: err => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Could not load registered pets.';
        this.pets = [];
        this.totalElements = 0;
      }
    });
  }

  onSearch(): void {
    this.currentPage = 0;
    this.fetchPets();
  }

  onSpeciesChange(): void {
    this.currentPage = 0;
    this.fetchPets();
  }

  onPageChange(page: number): void {
    this.currentPage = page;
    this.fetchPets();
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.currentPage = 0;
    this.fetchPets();
  }

  openPetDetails(pet: AdminPetSummary): void {
    this.isLoadingDetail = true;
    this.feedbackSuccessMessage = '';
    this.feedbackErrorMessage = '';
    this.showDeleteConfirm = false;

    this.http.get<any>(`${environment.apiUrl}/admin/pets/${pet.id}`).subscribe({
      next: res => {
        this.isLoadingDetail = false;
        if (res?.success && res.data) {
          this.selectedPetDetail = res.data;
          this.feedbackDraft = res.data.adminFeedback || '';
        }
      },
      error: err => {
        this.isLoadingDetail = false;
        this.feedbackErrorMessage = err.error?.message || 'Could not load pet details.';
      }
    });
  }

  closePetDetails(): void {
    this.selectedPetDetail = null;
    this.feedbackDraft = '';
    this.feedbackSuccessMessage = '';
    this.feedbackErrorMessage = '';
    this.showDeleteConfirm = false;
  }

  saveFeedback(): void {
    if (!this.selectedPetDetail || this.isSavingFeedback) return;

    const trimmed = (this.feedbackDraft || '').trim();
    if (trimmed.length > 2000) {
      this.feedbackErrorMessage = 'Feedback cannot exceed 2000 characters.';
      return;
    }

    this.isSavingFeedback = true;
    this.feedbackSuccessMessage = '';
    this.feedbackErrorMessage = '';

    const payload = { feedback: trimmed };

    this.http.put<any>(`${environment.apiUrl}/admin/pets/${this.selectedPetDetail.id}/feedback`, payload).subscribe({
      next: res => {
        this.isSavingFeedback = false;
        if (res?.success && res.data) {
          this.selectedPetDetail = res.data;
          this.feedbackDraft = res.data.adminFeedback || '';
          this.feedbackSuccessMessage = trimmed
            ? 'Suggestion published successfully.'
            : 'Suggestion cleared successfully.';
          this.refreshSummaryRow(res.data);
        }
      },
      error: err => {
        this.isSavingFeedback = false;
        this.feedbackErrorMessage = err.error?.message || 'Could not save suggestion.';
      }
    });
  }

  confirmDeleteFeedback(): void {
    this.showDeleteConfirm = true;
  }

  cancelDeleteFeedback(): void {
    this.showDeleteConfirm = false;
  }

  executeDeleteFeedback(): void {
    if (!this.selectedPetDetail || this.isDeletingFeedback) return;

    this.isDeletingFeedback = true;
    this.feedbackSuccessMessage = '';
    this.feedbackErrorMessage = '';

    this.http.delete<any>(`${environment.apiUrl}/admin/pets/${this.selectedPetDetail.id}/feedback`).subscribe({
      next: () => {
        this.isDeletingFeedback = false;
        this.showDeleteConfirm = false;
        if (this.selectedPetDetail) {
          this.selectedPetDetail.adminFeedback = undefined;
          this.selectedPetDetail.adminFeedbackUpdatedAt = undefined;
          this.selectedPetDetail.hasFeedback = false;
          this.feedbackDraft = '';
          this.feedbackSuccessMessage = 'Suggestion removed successfully.';
          this.refreshSummaryRow(this.selectedPetDetail);
        }
      },
      error: err => {
        this.isDeletingFeedback = false;
        this.feedbackErrorMessage = err.error?.message || 'Could not remove suggestion.';
      }
    });
  }

  private refreshSummaryRow(detail: AdminPetDetail): void {
    const idx = this.pets.findIndex(p => p.id === detail.id);
    if (idx !== -1) {
      this.pets[idx] = {
        ...this.pets[idx],
        hasFeedback: !!(detail.adminFeedback && detail.adminFeedback.trim()),
        adminFeedback: detail.adminFeedback,
        adminFeedbackUpdatedAt: detail.adminFeedbackUpdatedAt
      };
    }
  }

  calculateAge(dob?: string): string {
    if (!dob) return '—';
    try {
      const birth = new Date(dob);
      const now = new Date();
      let months = (now.getFullYear() - birth.getFullYear()) * 12 + (now.getMonth() - birth.getMonth());
      if (months < 0) return '—';
      if (months < 12) {
        return `${months} ${months === 1 ? 'mo' : 'mos'}`;
      }
      const years = Math.floor(months / 12);
      const rem = months % 12;
      return rem > 0 ? `${years}y ${rem}m` : `${years} ${years === 1 ? 'yr' : 'yrs'}`;
    } catch {
      return '—';
    }
  }

  getSpeciesIcon(type?: string): string {
    if (!type) return '🐾';
    const lower = type.toLowerCase();
    if (lower.includes('dog')) return '🐕';
    if (lower.includes('cat')) return '🐈';
    if (lower.includes('bird')) return '🦜';
    if (lower.includes('fish')) return '🐠';
    return '🐾';
  }
}
