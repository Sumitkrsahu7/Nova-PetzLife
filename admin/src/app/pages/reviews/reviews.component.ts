import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminReviewService, AdminReviewDto } from '../../core/services/admin-review.service';
import { AdminGoogleService, AdminGoogleReviewDto } from '../../core/services/admin-google.service';

@Component({
  selector: 'app-reviews',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reviews.component.html',
  styleUrls: ['./reviews.component.scss']
})
export class ReviewsComponent implements OnInit {
  activeTab: 'product' | 'google' = 'product';

  // Customer Product Reviews
  reviews: AdminReviewDto[] = [];
  statusFilter: string = 'ALL';
  isLoadingReviews: boolean = true;
  actionMessage: string = '';

  // Reply Modal / Drawer
  replyingReview: AdminReviewDto | null = null;
  replyText: string = '';

  // Google Reviews
  googleReviews: AdminGoogleReviewDto[] = [];
  isLoadingGoogle: boolean = false;
  isSyncing: boolean = false;
  syncMessage: string = '';

  // KPI Metrics
  totalReviewsCount: number = 0;
  pendingCount: number = 0;
  approvedCount: number = 0;
  avgRating: number = 0;

  constructor(
    private reviewService: AdminReviewService,
    private googleService: AdminGoogleService
  ) {}

  ngOnInit(): void {
    this.loadProductReviews();
    this.loadGoogleReviews();
  }

  loadProductReviews(): void {
    this.isLoadingReviews = true;
    this.reviewService.getAllReviews(this.statusFilter).subscribe({
      next: (list) => {
        this.reviews = list;
        this.isLoadingReviews = false;
        this.calculateMetrics();
      },
      error: (err) => {
        this.isLoadingReviews = false;
        console.error('Failed to load reviews', err);
      }
    });
  }

  calculateMetrics(): void {
    this.totalReviewsCount = this.reviews.length;
    this.pendingCount = this.reviews.filter(r => r.status === 'PENDING').length;
    this.approvedCount = this.reviews.filter(r => r.status === 'APPROVED').length;
    if (this.totalReviewsCount > 0) {
      const sum = this.reviews.reduce((acc, r) => acc + r.rating, 0);
      this.avgRating = Math.round((sum / this.totalReviewsCount) * 10) / 10;
    } else {
      this.avgRating = 0;
    }
  }

  onFilterChange(status: string): void {
    this.statusFilter = status;
    this.loadProductReviews();
  }

  approveReview(r: AdminReviewDto): void {
    this.reviewService.moderateReview(r.id, 'APPROVED', r.adminReply).subscribe({
      next: (updated) => {
        this.showToast(`Review #${r.id} approved successfully`);
        this.loadProductReviews();
      }
    });
  }

  rejectReview(r: AdminReviewDto): void {
    this.reviewService.moderateReview(r.id, 'REJECTED', r.adminReply).subscribe({
      next: (updated) => {
        this.showToast(`Review #${r.id} rejected`);
        this.loadProductReviews();
      }
    });
  }

  openReply(r: AdminReviewDto): void {
    this.replyingReview = r;
    this.replyText = r.adminReply || '';
  }

  cancelReply(): void {
    this.replyingReview = null;
    this.replyText = '';
  }

  saveReply(): void {
    if (!this.replyingReview) return;
    this.reviewService.moderateReview(this.replyingReview.id, this.replyingReview.status, this.replyText).subscribe({
      next: () => {
        this.showToast(`Staff response saved for review #${this.replyingReview?.id}`);
        this.cancelReply();
        this.loadProductReviews();
      }
    });
  }

  deleteReview(r: AdminReviewDto): void {
    if (confirm(`Are you sure you want to permanently delete this review for "${r.productName}"?`)) {
      this.reviewService.deleteReview(r.id).subscribe({
        next: () => {
          this.showToast(`Review deleted`);
          this.loadProductReviews();
        }
      });
    }
  }

  // Google Reviews Handlers
  loadGoogleReviews(): void {
    this.isLoadingGoogle = true;
    this.googleService.getAllReviews().subscribe({
      next: (list) => {
        this.googleReviews = list;
        this.isLoadingGoogle = false;
      },
      error: () => {
        this.isLoadingGoogle = false;
      }
    });
  }

  syncGoogle(): void {
    this.isSyncing = true;
    this.syncMessage = '';
    this.googleService.syncReviews().subscribe({
      next: (res) => {
        this.isSyncing = false;
        this.syncMessage = `Synchronized ${res.syncedCount} authentic reviews from Google Places API!`;
        this.loadGoogleReviews();
        setTimeout(() => { this.syncMessage = ''; }, 4000);
      },
      error: () => {
        this.isSyncing = false;
        this.syncMessage = 'Sync failed. Check Google Maps API Key in configuration.';
      }
    });
  }

  toggleGoogleFeature(r: AdminGoogleReviewDto): void {
    const newStatus = !r.isFeatured;
    this.googleService.toggleFeatured(r.id, newStatus).subscribe({
      next: (updated) => {
        r.isFeatured = updated.isFeatured;
        this.showToast(`Review feature status updated to: ${newStatus ? 'Featured on Homepage' : 'Hidden from Homepage'}`);
      }
    });
  }

  private showToast(msg: string): void {
    this.actionMessage = msg;
    setTimeout(() => { this.actionMessage = ''; }, 3500);
  }
}
