import { Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { LoginComponent } from './pages/login/login.component';
import { OrdersComponent } from './pages/orders/orders.component';
import { ReturnsComponent } from './pages/returns/returns.component';
import { CouponsComponent } from './pages/coupons/coupons.component';
import { AnalyticsComponent } from './pages/analytics/analytics.component';
import { SupportTicketsComponent } from './pages/support-tickets/support-tickets.component';
import { ReviewsComponent } from './pages/reviews/reviews.component';
import { AuditComponent } from './pages/audit/audit.component';
import { ProductsComponent } from './pages/products/products.component';
import { InventoryComponent } from './pages/inventory/inventory.component';
import { CategoriesComponent } from './pages/categories/categories.component';
import { BrandsComponent } from './pages/brands/brands.component';
import { DeliverySettingsComponent } from './pages/delivery-settings/delivery-settings.component';
import { CustomersComponent } from './pages/customers/customers.component';
import { StaffComponent } from './pages/staff/staff.component';
import { adminAuthGuard } from './core/guards/admin-auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'dashboard', component: DashboardComponent, canActivate: [adminAuthGuard] },
  { path: 'orders', component: OrdersComponent, canActivate: [adminAuthGuard] },
  { path: 'returns', component: ReturnsComponent, canActivate: [adminAuthGuard] },
  { path: 'coupons', component: CouponsComponent, canActivate: [adminAuthGuard] },
  { path: 'analytics', component: AnalyticsComponent, canActivate: [adminAuthGuard] },
  { path: 'support-tickets', component: SupportTicketsComponent, canActivate: [adminAuthGuard] },
  { path: 'reviews', component: ReviewsComponent, canActivate: [adminAuthGuard] },
  { path: 'audit', component: AuditComponent, canActivate: [adminAuthGuard] },
  { path: 'products', component: ProductsComponent, canActivate: [adminAuthGuard] },
  { path: 'inventory', component: InventoryComponent, canActivate: [adminAuthGuard] },
  { path: 'categories', component: CategoriesComponent, canActivate: [adminAuthGuard] },
  { path: 'brands', component: BrandsComponent, canActivate: [adminAuthGuard] },
  { path: 'delivery-settings', component: DeliverySettingsComponent, canActivate: [adminAuthGuard] },
  { path: 'customers', component: CustomersComponent, canActivate: [adminAuthGuard] },
  { path: 'staff', component: StaffComponent, canActivate: [adminAuthGuard] },
  { path: 'pricing', component: ProductsComponent, canActivate: [adminAuthGuard] },
  { path: '**', redirectTo: 'dashboard' }
];
