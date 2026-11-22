import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ReviewService, ReviewDto, ReviewSummaryDto, CreateReviewRequest } from '../../core/services/review.service';
import { CartService } from '../../core/services/cart.service';
import { environment } from '../../../environments/environment';
import { BackButtonComponent } from '../../components/back-button/back-button.component';

export interface ProductDetail {
  id: number;
  name: string;
  slug: string;
  description: string;
  sku: string;
  brandName: string;
  categoryName: string;
  basePrice: number;
  discountedPrice?: number;
  petType: string;
  lifeStage: string;
  productType: string;
  images: { id: number; imageUrl: string; altText: string; isPrimary: boolean; viewType?: string }[];
  variants: { id: number; variantName: string; price: number; discountedPrice?: number }[];
  returnPolicy: { returnAllowed: boolean; returnWindowDays: number; refundAllowed: boolean; customConditions?: string };
  availableStock: number;
}

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, BackButtonComponent],
  templateUrl: './product-detail.component.html',
  styleUrls: ['./product-detail.component.scss']
})
export class ProductDetailComponent implements OnInit {
  productId!: number;
  product: ProductDetail | null = null;
  selectedImageUrl: string = '';
  activeImageIndex: number = 0;
  selectedVariantId: number | null = null;
  quantity: number = 1;
  isLoading: boolean = true;
  cartMessage: string = '';

  // Reviews
  reviews: ReviewDto[] = [];
  summary: ReviewSummaryDto | null = null;
  isLoadingReviews: boolean = true;

  // New Review Form
  newRating: number = 5;
  newTitle: string = '';
  newComment: string = '';
  isSubmittingReview: boolean = false;
  reviewSuccessMessage: string = '';
  reviewErrorMessage: string = '';

  constructor(
    private route: ActivatedRoute,
    private http: HttpClient,
    private reviewService: ReviewService,
    private cartService: CartService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const idParam = params.get('id');
      if (idParam) {
        this.productId = +idParam;
        this.loadProduct();
        this.loadReviews();
      }
    });
  }

  loadProduct(): void {
    this.isLoading = true;
    this.http.get<{ success: boolean; data: ProductDetail }>(`${environment.apiUrl}/products/${this.productId}`)
      .subscribe({
        next: (res) => {
          this.product = res.data;
          this.isLoading = false;
          if (this.product.images && this.product.images.length > 0) {
            const primaryIdx = this.product.images.findIndex(img => img.isPrimary);
            this.activeImageIndex = primaryIdx >= 0 ? primaryIdx : 0;
            this.selectedImageUrl = this.product.images[this.activeImageIndex].imageUrl;
          } else {
            this.activeImageIndex = 0;
            this.selectedImageUrl = 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?auto=format&fit=crop&w=600&q=80';
          }
          if (this.product.variants && this.product.variants.length > 0) {
            this.selectedVariantId = this.product.variants[0].id;
          }
        },
        error: (err) => {
          this.isLoading = false;
          console.error('Failed to load product details', err);
        }
      });
  }

  loadReviews(): void {
    this.isLoadingReviews = true;
    this.reviewService.getProductReviews(this.productId).subscribe({
      next: (list) => {
        this.reviews = list;
        this.isLoadingReviews = false;
      },
      error: () => {
        this.isLoadingReviews = false;
      }
    });

    this.reviewService.getProductReviewSummary(this.productId).subscribe({
      next: (sum) => {
        this.summary = sum;
      }
    });
  }

  selectImage(url: string): void {
    this.selectedImageUrl = url;
    if (this.product?.images) {
      const idx = this.product.images.findIndex(i => i.imageUrl === url);
      if (idx >= 0) this.activeImageIndex = idx;
    }
  }

  selectImageByIndex(idx: number): void {
    if (this.product?.images && idx >= 0 && idx < this.product.images.length) {
      this.activeImageIndex = idx;
      this.selectedImageUrl = this.product.images[idx].imageUrl;
    }
  }

  prevImage(): void {
    if (!this.product?.images || this.product.images.length <= 1) return;
    this.activeImageIndex = (this.activeImageIndex - 1 + this.product.images.length) % this.product.images.length;
    this.selectedImageUrl = this.product.images[this.activeImageIndex].imageUrl;
  }

  nextImage(): void {
    if (!this.product?.images || this.product.images.length <= 1) return;
    this.activeImageIndex = (this.activeImageIndex + 1) % this.product.images.length;
    this.selectedImageUrl = this.product.images[this.activeImageIndex].imageUrl;
  }

  get currentViewLabel(): string {
    return this.getViewLabelForIndex(this.activeImageIndex);
  }

  getViewLabelForIndex(idx: number): string {
    if (!this.product?.images || this.product.images.length === 0) return 'Product View';
    const img = this.product.images[idx];
    if (img?.viewType) {
      if (img.viewType.toUpperCase() === 'FRONT') return 'Front View';
      if (img.viewType.toUpperCase() === 'BACK') return 'Back View';
      return 'Additional View';
    }
    if (idx === 0) return 'Front View';
    if (idx === 1) return 'Back View';
    return 'Angle ' + (idx + 1);
  }

  get currentDisplayPrice(): number {
    if (!this.product) return 0;
    if (this.selectedVariantId && this.product.variants) {
      const v = this.product.variants.find(x => x.id === this.selectedVariantId);
      if (v) return v.discountedPrice || v.price;
    }
    return this.product.discountedPrice || this.product.basePrice || 0;
  }

  setRating(star: number): void {
    this.newRating = star;
  }

  addToCart(): void {
    if (!this.product) return;
    const variant = this.selectedVariantId && this.product.variants
      ? this.product.variants.find(v => v.id === this.selectedVariantId)
      : null;
    const price = variant ? (variant.discountedPrice || variant.price) : (this.product.discountedPrice || this.product.basePrice);
    this.cartService.addItem(this.product.id, this.selectedVariantId || undefined, this.quantity, {
      productName: this.product.name,
      brandName: this.product.brandName || 'Nova Petzlife',
      primaryImageUrl: this.product.images?.[0]?.imageUrl,
      unitPrice: price,
      variantName: variant?.variantName,
      availableStock: this.product.availableStock || 15,
      returnAllowed: this.product.returnPolicy?.returnAllowed ?? true,
      returnWindowDays: this.product.returnPolicy?.returnWindowDays || 7
    }).subscribe();
    this.cartMessage = `Added ${this.quantity} item(s) to cart!`;
    setTimeout(() => { this.cartMessage = ''; }, 3500);
  }

  submitReview(): void {
    if (!this.newComment.trim()) {
      this.reviewErrorMessage = 'Please enter a review comment.';
      return;
    }

    this.isSubmittingReview = true;
    this.reviewErrorMessage = '';
    this.reviewSuccessMessage = '';

    const req: CreateReviewRequest = {
      productId: this.productId,
      rating: this.newRating,
      title: this.newTitle.trim() || undefined,
      comment: this.newComment.trim()
    };

    this.reviewService.submitReview(req).subscribe({
      next: (savedReview) => {
        this.isSubmittingReview = false;
        this.reviewSuccessMessage = 'Thank you! Your review has been submitted and published.';
        this.newTitle = '';
        this.newComment = '';
        this.newRating = 5;
        this.loadReviews();
        setTimeout(() => { this.reviewSuccessMessage = ''; }, 5000);
      },
      error: (err) => {
        this.isSubmittingReview = false;
        this.reviewErrorMessage = err.error?.message || 'Failed to submit review. Please try again.';
      }
    });
  }
}
