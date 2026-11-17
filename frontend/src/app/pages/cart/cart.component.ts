import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { CartService, Cart, CartItem } from '../../core/services/cart.service';

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.scss']
})
export class CartComponent implements OnInit {
  cart: Cart | null = null;
  freeDeliveryThreshold = 500;
  standardDeliveryFee = 50;

  constructor(public cartService: CartService) {}

  ngOnInit(): void {
    this.cartService.cart$.subscribe(cart => {
      this.cart = cart;
    });
  }

  get remainingForFreeDelivery(): number {
    if (!this.cart) return this.freeDeliveryThreshold;
    return Math.max(0, this.freeDeliveryThreshold - this.cart.subtotal);
  }

  get deliveryFee(): number {
    if (!this.cart || this.cart.subtotal === 0) return 0;
    return this.cart.subtotal >= this.freeDeliveryThreshold ? 0 : this.standardDeliveryFee;
  }

  get totalPayable(): number {
    if (!this.cart) return 0;
    return this.cart.subtotal + this.deliveryFee;
  }

  updateQuantity(item: CartItem, newQty: number): void {
    if (newQty < 0) return;
    this.cartService.updateQuantity(item.id, newQty).subscribe();
  }

  removeItem(item: CartItem): void {
    this.cartService.removeItem(item.id).subscribe();
  }

  clearCart(): void {
    if (confirm('Are you sure you want to clear your shopping cart?')) {
      this.cartService.clearCart().subscribe();
    }
  }
}
