import { Routes } from '@angular/router';
import { HomeComponent } from './pages/home/home.component';
import { CartComponent } from './pages/cart/cart.component';
import { CheckoutComponent } from './pages/checkout/checkout.component';
import { OrderTrackingComponent } from './pages/order-tracking/order-tracking.component';
import { InvoiceComponent } from './pages/invoice/invoice.component';
import { PetsComponent } from './pages/pets/pets.component';
import { SupportComponent } from './pages/support/support.component';
import { ProductDetailComponent } from './pages/product-detail/product-detail.component';
import { ShopComponent } from './pages/shop/shop.component';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';
import { ForgotPasswordComponent } from './pages/forgot-password/forgot-password.component';
import { ResetPasswordComponent } from './pages/reset-password/reset-password.component';
import { AccountComponent } from './pages/account/account.component';
import { BrandsComponent } from './pages/brands/brands.component';
import { DeliveryComponent } from './pages/delivery/delivery.component';
import { PoliciesComponent } from './pages/policies/policies.component';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent, pathMatch: 'full' },
  { path: 'home', redirectTo: '', pathMatch: 'full' },
  { path: 'shop', component: ShopComponent },
  { path: 'brands', component: BrandsComponent },
  { path: 'check-delivery', component: DeliveryComponent },
  { path: 'delivery', redirectTo: 'check-delivery', pathMatch: 'full' },
  { path: 'product/:id', component: ProductDetailComponent },
  { path: 'cart', component: CartComponent },
  { path: 'checkout', component: CheckoutComponent, canActivate: [authGuard] },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'forgot-password', component: ForgotPasswordComponent },
  { path: 'reset-password', component: ResetPasswordComponent },
  { path: 'account', component: AccountComponent, canActivate: [authGuard] },
  { path: 'profile', component: AccountComponent, canActivate: [authGuard] },
  { path: 'orders', redirectTo: 'account?tab=orders', pathMatch: 'full' },
  { path: 'order-history', redirectTo: 'account?tab=orders', pathMatch: 'full' },
  { path: 'my-pets', component: PetsComponent, canActivate: [authGuard] },
  { path: 'support', component: SupportComponent },
  { path: 'policies', component: PoliciesComponent },
  { path: 'policies/:slug', component: PoliciesComponent },
  { path: 'shipping', redirectTo: 'policies/shipping', pathMatch: 'full' },
  { path: 'returns', redirectTo: 'policies/returns', pathMatch: 'full' },
  { path: 'terms', redirectTo: 'policies/terms', pathMatch: 'full' },
  { path: 'privacy', redirectTo: 'policies/privacy', pathMatch: 'full' },
  { path: 'faq', redirectTo: 'support', pathMatch: 'full' },
  { path: 'faqs', redirectTo: 'support', pathMatch: 'full' },
  { path: 'about', redirectTo: 'check-delivery', pathMatch: 'full' },
  { path: 'track/:orderNumber', component: OrderTrackingComponent },
  { path: 'invoice/:orderNumber', component: InvoiceComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '' }
];
