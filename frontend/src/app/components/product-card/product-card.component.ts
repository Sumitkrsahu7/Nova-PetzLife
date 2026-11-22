import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ProductCardData, ProductVariant } from '../../models/product.model';

@Component({
  selector: 'app-product-card',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './product-card.component.html',
  styleUrls: ['./product-card.component.scss']
})
export class ProductCardComponent {
  @Input({ required: true }) product!: ProductCardData;
  @Output() addToCart = new EventEmitter<{ product: ProductCardData; variant?: ProductVariant }>();
  @Output() toggleWishlist = new EventEmitter<ProductCardData>();

  selectedVariant?: ProductVariant;
  isWishlisted: boolean = false;
  addedFeedback: boolean = false;
  isHovered: boolean = false;

  get displayImageUrl(): string {
    if (this.isHovered && this.product.backImageUrl) {
      return this.product.backImageUrl;
    }
    return this.product.imageUrl;
  }

  get currentPrice(): number {
    if (this.selectedVariant) {
      return this.selectedVariant.discountedPrice || this.selectedVariant.price;
    }
    return this.product.discountedPrice || this.product.price;
  }

  get originalPrice(): number | undefined {
    if (this.selectedVariant && this.selectedVariant.discountedPrice) {
      return this.selectedVariant.price;
    }
    return this.product.discountedPrice ? this.product.price : undefined;
  }

  get discountPercent(): number | null {
    if (this.originalPrice && this.originalPrice > this.currentPrice) {
      return Math.round(((this.originalPrice - this.currentPrice) / this.originalPrice) * 100);
    }
    return null;
  }

  selectVariant(variant: ProductVariant): void {
    this.selectedVariant = variant;
  }

  onAddToCart(): void {
    if (!this.product.inStock) return;
    this.addToCart.emit({
      product: this.product,
      variant: this.selectedVariant
    });

    this.addedFeedback = true;
    setTimeout(() => {
      this.addedFeedback = false;
    }, 1200);
  }

  onToggleWishlist(): void {
    this.isWishlisted = !this.isWishlisted;
    this.toggleWishlist.emit(this.product);
  }
}
