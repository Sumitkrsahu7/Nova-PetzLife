import { Component, Input, OnInit, HostBinding } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AdminUiService } from '../../core/services/admin-ui.service';
import { AdminAuthService } from '../../core/services/admin-auth.service';

export interface NavItem {
  label: string;
  icon: string;
  route: string;
  badge?: string;
  badgeType?: 'warning' | 'danger' | 'success';
  permission?: string;
  superAdminOnly?: boolean;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss']
})
export class SidebarComponent implements OnInit {
  @Input() currentUserRole: 'ROLE_SUPER_ADMIN' | 'ROLE_STAFF_ADMIN' = 'ROLE_SUPER_ADMIN';
  @Input() currentUserName: string = 'Super Admin';

  isCollapsed = false;
  showLogoutConfirm = false;

  @HostBinding('class.collapsed')
  get hostCollapsed(): boolean {
    return this.isCollapsed;
  }

  constructor(
    public adminUi: AdminUiService,
    private adminAuthService: AdminAuthService,
    private router: Router
  ) {}

  navGroups: { title: string; items: NavItem[] }[] = [
    {
      title: 'OPERATIONS',
      items: [
        { label: 'Dashboard', icon: '📊', route: '/dashboard' },
        { label: 'Orders & Dispatch', icon: '📦', route: '/orders', badge: 'Active', badgeType: 'success', permission: 'ORDER_VIEW' },
        { label: 'Customers & Accounts', icon: '👥', route: '/customers' },
        { label: 'Registered Pets', icon: '🐾', route: '/pets' },
        { label: 'Inventory & Stock', icon: '📋', route: '/inventory', permission: 'INVENTORY_VIEW' },
        { label: 'Returns & Refunds', icon: '🔄', route: '/returns', badge: 'Review', badgeType: 'warning', permission: 'ORDER_VIEW' },
        { label: 'Customer Inquiries', icon: '💬', route: '/support-tickets', badge: 'Help', badgeType: 'warning', permission: 'CUSTOMER_SUPPORT_VIEW' }
      ]
    },
    {
      title: 'CATALOG & PRICING',
      items: [
        { label: 'Products & Variants', icon: '🏷️', route: '/products', permission: 'PRODUCT_VIEW' },
        { label: 'Category Hierarchy', icon: '📂', route: '/categories', permission: 'PRODUCT_VIEW' },
        { label: 'Brand Isolation', icon: '✨', route: '/brands', permission: 'PRODUCT_VIEW' },
        { label: 'Promotions & Coupons', icon: '🎟️', route: '/coupons', badge: 'Active', badgeType: 'success', permission: 'PRODUCT_VIEW' },
        { label: 'Pricing Engine & GST', icon: '💰', route: '/pricing', permission: 'PRODUCT_VIEW' }
      ]
    },
    {
      title: 'SETTINGS & COMPLIANCE',
      items: [
        { label: 'Business Analytics & Sales', icon: '📈', route: '/analytics', badge: 'Live', badgeType: 'success' },
        { label: 'Sales Reports & Export', icon: '📑', route: '/sales-reports', badge: 'Audited', badgeType: 'success' },
        { label: 'Store & Delivery Settings', icon: '📍', route: '/delivery-settings', permission: 'STORE_SETTINGS_EDIT' },
        { label: 'Staff & Permissions', icon: '👑', route: '/staff', permission: 'STAFF_MANAGE', superAdminOnly: true },
        { label: 'Website Product Reviews', icon: '⭐', route: '/reviews' },
        { label: 'Audit & Security Logs', icon: '🛡️', route: '/audit', superAdminOnly: true }
      ]
    }
  ];

  canShow(item: NavItem): boolean {
    if (this.currentUserRole === 'ROLE_SUPER_ADMIN') return true;
    if (item.superAdminOnly) return false;
    if (item.permission) {
      return this.adminAuthService.hasPermission(item.permission);
    }
    return true;
  }

  ngOnInit(): void {
    this.adminUi.isSidebarCollapsed$.subscribe(collapsed => {
      this.isCollapsed = collapsed;
    });

    this.adminAuthService.currentUser$.subscribe(user => {
      if (user) {
        this.currentUserName = `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.email;
        if (user.roles?.includes('ROLE_SUPER_ADMIN')) {
          this.currentUserRole = 'ROLE_SUPER_ADMIN';
        } else if (user.roles?.includes('ROLE_STAFF_ADMIN')) {
          this.currentUserRole = 'ROLE_STAFF_ADMIN';
        }
      }
    });
  }

  toggleSidebar(): void {
    this.adminUi.toggleCollapse();
  }

  requestLogout(): void {
    this.showLogoutConfirm = true;
  }

  cancelLogout(): void {
    this.showLogoutConfirm = false;
  }

  confirmLogout(): void {
    this.showLogoutConfirm = false;
    this.adminUi.closeDrawer();
    this.adminAuthService.logout();
    this.router.navigate(['/login']);
  }

  logout(): void {
    this.requestLogout();
  }
}
