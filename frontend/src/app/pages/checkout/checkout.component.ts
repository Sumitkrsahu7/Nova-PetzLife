import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { CartService, Cart } from '../../core/services/cart.service';
import { DeliveryService, DeliveryEligibility, DeliverySlot } from '../../core/services/delivery.service';
import { OrderService, CheckoutPayload } from '../../core/services/order.service';
import { PaymentService } from '../../core/services/payment.service';
import { PromotionService, Coupon } from '../../core/services/promotion.service';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.scss']
})
export class CheckoutComponent implements OnInit {
  cart: Cart | null = null;
  slots: DeliverySlot[] = [];
  eligibility: DeliveryEligibility | null = null;

  // Promotions & Coupon Management
  activeCoupons: Coupon[] = [];
  couponCodeInput = '';
  appliedCouponCode: string | null = null;
  discountAmount = 0;
  couponValidationMessage = '';
  couponSuccessMessage = '';
  isApplyingCoupon = false;

  isCheckingDelivery = false;
  isSubmitting = false;
  errorMessage = '';

  // Form Fields
  recipientName = '';
  phone = '';
  email = '';
  addressLine1 = '';
  addressLine2 = '';
  landmark = '';
  city = 'Thiruvananthapuram';
  state = 'Kerala';
  postalCode = '695004';

  // Coordinate Selection (Defaults to Pattom ~1.1 km from Kuravankonam store)
  latitude = 8.5244;
  longitude = 76.9442;

  selectedSlot = '';
  paymentMethod = 'COD'; // Default to Cash on Delivery
  orderNotes = '';

  // Trivandrum Popular Neighbourhood Presets
  trivandrumLocations = [
    { name: 'Pattom Junction', lat: 8.5244, lon: 76.9442, pin: '695004' },
    { name: 'Kowdiar Palace', lat: 8.5241, lon: 76.9602, pin: '695003' },
    { name: 'Ambalamukku', lat: 8.5320, lon: 76.9550, pin: '695005' },
    { name: 'Palayam / Statue', lat: 8.5020, lon: 76.9500, pin: '695001' },
    { name: 'Sasthamangalam', lat: 8.5140, lon: 76.9740, pin: '695010' },
    { name: 'Technopark / Kazhakkoottam', lat: 8.5583, lon: 76.8812, pin: '695581' }
  ];

  constructor(
    public cartService: CartService,
    private deliveryService: DeliveryService,
    private orderService: OrderService,
    private paymentService: PaymentService,
    private promotionService: PromotionService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.cartService.cart$.subscribe(cart => {
      this.cart = cart;
      if (cart && cart.items.length > 0) {
        this.runDeliveryCheck();
        if (this.appliedCouponCode) {
          this.applyCoupon(this.appliedCouponCode);
        }
      }
    });

    this.loadActiveCoupons();

    this.deliveryService.getSlots().subscribe(res => {
      if (res?.success && res.data) {
        this.slots = res.data;
        if (this.slots.length > 0) {
          this.selectedSlot = this.slots[0].slotName;
        }
      }
    });
  }

  loadActiveCoupons(): void {
    this.promotionService.getActiveCoupons().subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.activeCoupons = res.data;
        }
      },
      error: () => {
        this.activeCoupons = [];
      }
    });
  }

  applyCoupon(codeToApply?: string): void {
    const code = (codeToApply || this.couponCodeInput).trim().toUpperCase();
    if (!code) {
      this.couponValidationMessage = 'Please enter a coupon code';
      this.couponSuccessMessage = '';
      return;
    }

    const subtotal = this.cart ? this.cart.subtotal : 0;
    if (subtotal <= 0) {
      this.couponValidationMessage = 'Cart is empty';
      return;
    }

    this.isApplyingCoupon = true;
    this.couponValidationMessage = '';
    this.couponSuccessMessage = '';

    this.promotionService.validateCoupon(code, subtotal).subscribe({
      next: res => {
        this.isApplyingCoupon = false;
        if (res?.success && res.data?.valid) {
          this.appliedCouponCode = res.data.code;
          this.discountAmount = res.data.discountAmount || 0;
          this.couponSuccessMessage = `Coupon "${res.data.code}" applied! You save ₹${this.discountAmount.toFixed(2)}`;
          this.couponValidationMessage = '';
          this.couponCodeInput = res.data.code;
        } else {
          this.couponValidationMessage = res?.data?.message || 'Invalid or expired coupon';
          this.appliedCouponCode = null;
          this.discountAmount = 0;
        }
      },
      error: err => {
        this.isApplyingCoupon = false;
        this.couponValidationMessage = err.error?.message || 'Could not validate coupon code';
        this.appliedCouponCode = null;
        this.discountAmount = 0;
      }
    });
  }

  removeCoupon(): void {
    this.appliedCouponCode = null;
    this.discountAmount = 0;
    this.couponCodeInput = '';
    this.couponSuccessMessage = '';
    this.couponValidationMessage = '';
  }

  selectPreset(loc: any): void {
    this.latitude = loc.lat;
    this.longitude = loc.lon;
    this.postalCode = loc.pin;
    this.runDeliveryCheck();
  }

  runDeliveryCheck(): void {
    this.isCheckingDelivery = true;
    this.errorMessage = '';
    const subtotal = this.cart ? this.cart.subtotal : 0;

    this.deliveryService.checkEligibility(this.latitude, this.longitude, subtotal, this.postalCode).subscribe({
      next: res => {
        this.isCheckingDelivery = false;
        if (res?.success) {
          this.eligibility = res.data;
        }
      },
      error: err => {
        this.isCheckingDelivery = false;
        this.errorMessage = err.error?.message || 'Could not verify delivery eligibility';
      }
    });
  }

  get deliveryFee(): number {
    return this.eligibility ? this.eligibility.deliveryCharge : 50;
  }

  get totalPayable(): number {
    if (!this.cart) return 0;
    return Math.max(0, this.cart.subtotal + this.deliveryFee - this.discountAmount);
  }

  placeOrder(): void {
    if (!this.recipientName.trim() || !this.phone.trim() || !this.addressLine1.trim()) {
      this.errorMessage = 'Please complete all required recipient and address fields.';
      return;
    }

    if (this.eligibility && !this.eligibility.isEligible) {
      this.errorMessage = 'Sorry, delivery is not currently available at this location. Please choose a serviceable delivery address.';
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = '';

    const payload: CheckoutPayload = {
      sessionToken: this.cartService.getSessionToken(),
      guestEmail: this.email.trim() || undefined,
      guestPhone: this.phone.trim(),
      guestFirstName: this.recipientName.split(' ')[0] || 'Customer',
      guestLastName: this.recipientName.split(' ').slice(1).join(' ') || '',
      address: {
        recipientName: this.recipientName.trim(),
        phone: this.phone.trim(),
        addressLine1: this.addressLine1.trim(),
        addressLine2: this.addressLine2.trim() || undefined,
        landmark: this.landmark.trim() || undefined,
        city: this.city,
        state: this.state,
        postalCode: this.postalCode,
        latitude: this.latitude,
        longitude: this.longitude,
        isDefault: true
      },
      latitude: this.latitude,
      longitude: this.longitude,
      deliverySlot: this.selectedSlot,
      paymentMethod: this.paymentMethod,
      couponCode: this.appliedCouponCode || undefined,
      notes: this.orderNotes.trim() || undefined
    };

    this.orderService.checkout(payload).subscribe({
      next: res => {
        if (res?.success && res.data) {
          const orderNumber = res.data.orderNumber;

          if (this.paymentMethod === 'RAZORPAY') {
            this.initiateRazorpayPayment(orderNumber);
          } else {
            // Cash on Delivery
            this.isSubmitting = false;
            this.cartService.clearCart().subscribe();
            this.router.navigate(['/invoice', orderNumber]);
          }
        }
      },
      error: err => {
        this.isSubmitting = false;
        this.errorMessage = err.error?.message || 'Failed to place order. Please try again.';
      }
    });
  }

  private initiateRazorpayPayment(orderNumber: string): void {
    this.paymentService.createPaymentOrder(orderNumber).subscribe({
      next: payRes => {
        if (payRes?.success && payRes.data) {
          const paymentOrder = payRes.data;

          if (typeof (window as any).Razorpay !== 'undefined') {
            const options = {
              key: paymentOrder.keyId,
              amount: paymentOrder.amountInPaise,
              currency: paymentOrder.currency,
              name: 'NOVA PETZLIFE',
              description: `Payment for Order #${orderNumber}`,
              order_id: paymentOrder.razorpayOrderId,
              prefill: {
                name: paymentOrder.customerName,
                email: paymentOrder.customerEmail,
                contact: paymentOrder.customerPhone
              },
              theme: {
                color: '#248B58'
              },
              handler: (response: any) => {
                this.verifyAndRedirect(
                  orderNumber,
                  response.razorpay_order_id,
                  response.razorpay_payment_id,
                  response.razorpay_signature
                );
              },
              modal: {
                ondismiss: () => {
                  this.isSubmitting = false;
                  this.errorMessage = 'Payment was cancelled or dismissed. You can complete it anytime.';
                  this.router.navigate(['/track', orderNumber]);
                }
              }
            };
            const rzp = new (window as any).Razorpay(options);
            rzp.open();
          } else {
            // Offline / Sandbox Simulation Fallback
            this.verifyAndRedirect(
              orderNumber,
              paymentOrder.razorpayOrderId,
              'pay_sim_' + Math.random().toString(36).substring(2, 10),
              'simulated_rzp_signature'
            );
          }
        }
      },
      error: err => {
        this.isSubmitting = false;
        this.errorMessage = err.error?.message || 'Could not initiate Razorpay payment.';
        this.router.navigate(['/track', orderNumber]);
      }
    });
  }

  private verifyAndRedirect(orderNumber: string, razorpayOrderId: string, razorpayPaymentId: string, razorpaySignature: string): void {
    this.paymentService.verifyPayment({
      orderNumber,
      razorpayOrderId,
      razorpayPaymentId,
      razorpaySignature
    }).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.cartService.clearCart().subscribe();
        this.router.navigate(['/invoice', orderNumber]);
      },
      error: err => {
        this.isSubmitting = false;
        this.errorMessage = err.error?.message || 'Payment signature verification failed.';
        this.router.navigate(['/track', orderNumber]);
      }
    });
  }
}
