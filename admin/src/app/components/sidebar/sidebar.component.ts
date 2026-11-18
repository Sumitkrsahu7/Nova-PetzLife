import { Component, Input, OnInit } from '@angular/core';
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

  constructor(
    public adminUi: AdminUiService,
    private adminAuthService: AdminAuthService,
    private router: Router
  ) {}

  navGroups = [
    {
      title: 'OPERATIONS',
      items: [
        { label: 'Dashboard', icon: '📊', route: '/dashboard' },
        { label: 'Orders & Dispatch', icon: '📦', route: '/orders', badge: 'Active', badgeType: 'success' },
        { label: 'Customers & Accounts', icon: '👥', route: '/customers' },
        { label: 'Inventory & Stock', icon: '📋', route: '/inventory' },
        { label: 'Returns & Refunds', icon: '🔄', route: '/returns', badge: 'Review', badgeType: 'warning' },
        { label: 'Customer Inquiries', icon: '💬', route: '/support-tickets', badge: 'Help', badgeType: 'warning' }
      ]
    },
    {
      title: 'CATALOG & PRICING',
      items: [
        { label: 'Products & Variants', icon: '🏷️', route: '/products' },
        { label: 'Category Hierarchy', icon: '📂', route: '/categories' },
        { label: 'Brand Isolation', icon: '✨', route: '/brands' },
        { label: 'Promotions & Coupons', icon: '🎟️', route: '/coupons', badge: 'Active', badgeType: 'success' },
        { label: 'Pricing Engine & GST', icon: '💰', route: '/pricing' }
      ]
    },
    {
      title: 'SETTINGS & COMPLIANCE',
      items: [
        { label: 'Business Analytics & Sales', icon: '📈', route: '/analytics', badge: 'Live', badgeType: 'success' },
        { label: 'Kuravankonam 20 km Delivery', icon: '📍', route: '/delivery-settings', badge: 'Verify', badgeType: 'danger' },
        { label: 'Website Product Reviews', icon: '⭐', route: '/reviews' },
        { label: 'Audit & Security Logs', icon: '🛡️', route: '/audit' }
      ]
    }
  ];

  ngOnInit(): void {
    const saved = localStorage.getItem('nova_admin_sidebar_collapsed');
    this.isCollapsed = saved === 'true';

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
    this.isCollapsed = !this.isCollapsed;
    localStorage.setItem('nova_admin_sidebar_collapsed', String(this.isCollapsed));
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
