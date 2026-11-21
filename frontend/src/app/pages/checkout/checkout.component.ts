import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { CartService, Cart } from '../../core/services/cart.service';
import { AuthService } from '../../core/services/auth.service';
import { DeliveryService, DeliveryEligibility, DeliverySlot } from '../../core/services/delivery.service';
import { OrderService, CheckoutPayload } from '../../core/services/order.service';
import { PaymentService } from '../../core/services/payment.service';
import { PromotionService, Coupon } from '../../core/services/promotion.service';
import { BackButtonComponent } from '../../components/back-button/back-button.component';
import { environment } from '../../../environments/environment';

export interface SavedAddress {
  id?: number;
  recipientName: string;
  phone: string;
  addressLine1: string;
  addressLine2?: string;
  landmark?: string;
  city: string;
  state: string;
  postalCode: string;
  latitude?: number;
  longitude?: number;
  isDefault?: boolean;
}

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, BackButtonComponent],
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.scss']
})
export class CheckoutComponent implements OnInit {
  cart: Cart | null = null;
  slots: DeliverySlot[] = [];
  eligibility: DeliveryEligibility | null = null;

  // Address Selection & Management
  savedAddresses: SavedAddress[] = [];
  selectedAddressId: number | null = null;
  useNewAddress = false;
  saveAddressToProfile = true;
  isLoadingAddresses = false;

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

  // Coordinate Selection (Defaults to Pattom ~1.1 km from our store)
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
    private authService: AuthService,
    private http: HttpClient,
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

    this.loadSavedAddresses();
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

  loadSavedAddresses(): void {
    this.isLoadingAddresses = true;
    this.http.get<any>(`${environment.apiUrl}/customer/addresses`).subscribe({
      next: res => {
        this.isLoadingAddresses = false;
        if (res?.success && Array.isArray(res.data) && res.data.length > 0) {
          this.savedAddresses = res.data;
          const defaultAddr = this.savedAddresses.find(a => a.isDefault) || this.savedAddresses[0];
          this.selectSavedAddress(defaultAddr);
        } else {
          this.savedAddresses = [];
          this.useNewAddress = true;
          this.prefillUserProfile();
        }
      },
      error: () => {
        this.isLoadingAddresses = false;
        this.savedAddresses = [];
        this.useNewAddress = true;
        this.prefillUserProfile();
      }
    });
  }

  selectSavedAddress(addr: SavedAddress): void {
    this.selectedAddressId = addr.id || null;
    this.useNewAddress = false;
    this.recipientName = addr.recipientName || '';
    this.phone = addr.phone || '';
    this.addressLine1 = addr.addressLine1 || '';
    this.addressLine2 = addr.addressLine2 || '';
    this.landmark = addr.landmark || '';
    this.city = addr.city || 'Thiruvananthapuram';
    this.state = addr.state || 'Kerala';
    this.postalCode = addr.postalCode || '695004';
    if (addr.latitude != null && addr.longitude != null) {
      this.latitude = Number(addr.latitude);
      this.longitude = Number(addr.longitude);
    }
    this.runDeliveryCheck();
  }

  switchToNewAddress(): void {
    this.useNewAddress = true;
    this.selectedAddressId = null;
    this.prefillUserProfile();
    this.runDeliveryCheck();
  }

  cancelNewAddress(): void {
    if (this.savedAddresses.length > 0) {
      this.useNewAddress = false;
      const defaultAddr = this.savedAddresses.find(a => a.isDefault) || this.savedAddresses[0];
      this.selectSavedAddress(defaultAddr);
    }
  }

  prefillUserProfile(): void {
    const user = this.authService.getCurrentUser();
    if (user) {
      if (!this.recipientName) {
        this.recipientName = `${user.firstName || ''} ${user.lastName || ''}`.trim();
      }
      if (!this.phone && user.phone) {
        this.phone = user.phone;
      }
      if (!this.email && user.email) {
        this.email = user.email;
      }
    }
  }

  get isAddressValid(): boolean {
    if (this.isCheckingDelivery) return false;
    if (this.eligibility && !this.eligibility.isEligible) return false;

    if (!this.useNewAddress && this.selectedAddressId !== null) {
      return true;
    }

    const cleanPhone = (this.phone || '').replace(/\D/g, '');
    const hasName = !!this.recipientName && this.recipientName.trim().length >= 2;
    const hasPhone = cleanPhone.length >= 10;
    const hasLine1 = !!this.addressLine1 && this.addressLine1.trim().length >= 3;
    const hasCity = !!this.city && this.city.trim().length >= 2;
    const hasState = !!this.state && this.state.trim().length >= 2;
    const hasPin = !!this.postalCode && this.postalCode.trim().length >= 6;

    return hasName && hasPhone && hasLine1 && hasCity && hasState && hasPin;
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
    if (!this.isAddressValid) {
      if (this.eligibility && !this.eligibility.isEligible) {
        this.errorMessage = 'Sorry, delivery is not currently available at this location. Please choose a serviceable delivery address within our 20 km radius.';
      } else {
        this.errorMessage = 'Please select or complete a valid delivery address with all required fields (Recipient Name, 10-digit Phone, Address Line 1, City, State, and 6-digit PIN code).';
      }
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = '';

    // If customer entered a new address and chose to save to profile
    if (this.useNewAddress && this.saveAddressToProfile) {
      const newAddressPayload = {
        recipientName: this.recipientName.trim(),
        phone: this.phone.trim(),
        addressLine1: this.addressLine1.trim(),
        addressLine2: this.addressLine2.trim() || undefined,
        landmark: this.landmark.trim() || undefined,
        city: this.city.trim(),
        state: this.state.trim(),
        postalCode: this.postalCode.trim(),
        latitude: this.latitude,
        longitude: this.longitude,
        isDefault: this.savedAddresses.length === 0
      };

      this.http.post<any>(`${environment.apiUrl}/customer/addresses`, newAddressPayload).subscribe({
        next: addrRes => {
          const newAddressId = addrRes?.data?.id;
          this.executeCheckout(newAddressId);
        },
        error: () => {
          // Even if saving to profile fails, proceed with the inline address in checkout
          this.executeCheckout(undefined);
        }
      });
    } else {
      this.executeCheckout(!this.useNewAddress && this.selectedAddressId ? this.selectedAddressId : undefined);
    }
  }

  private executeCheckout(addressId?: number): void {
    const payload: CheckoutPayload = {
      cartId: this.cart?.id,
      sessionToken: this.cartService.getSessionToken(),
      guestEmail: this.email.trim() || undefined,
      guestPhone: this.phone.trim(),
      guestFirstName: this.recipientName.split(' ')[0] || 'Customer',
      guestLastName: this.recipientName.split(' ').slice(1).join(' ') || '',
      addressId: addressId,
      address: !addressId ? {
        recipientName: this.recipientName.trim(),
        phone: this.phone.trim(),
        addressLine1: this.addressLine1.trim(),
        addressLine2: this.addressLine2.trim() || undefined,
        landmark: this.landmark.trim() || undefined,
        city: this.city.trim(),
        state: this.state.trim(),
        postalCode: this.postalCode.trim(),
        latitude: this.latitude,
        longitude: this.longitude,
        isDefault: this.savedAddresses.length === 0
      } : undefined,
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

