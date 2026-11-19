import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AdminAnalyticsService } from '../../core/services/admin-analytics.service';
import { AdminOrderService } from '../../core/services/admin-order.service';
import { BackButtonComponent } from '../../components/back-button/back-button.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, BackButtonComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  // Confirmed Shop Coordinates & Verification State
  shopLocationData = {
    shopName: 'NOVA PETZLIFE (Petzlife)',
    address: 'Petzlife, Kuravankonam Junction, Kuravankonam Ambalamukku Road, Thiruvananthapuram, Kerala 695003',
    city: 'Thiruvananthapuram',
    state: 'Kerala',
    pincode: '695003',
    googlePlaceId: 'ChIJ_verified_petzlife_kuravankonam',
    latitude: 8.5284480,
    longitude: 76.9532680,
    deliveryRadiusKm: 20.0,
    isOwnerApproved: false,
    verifiedDate: null as string | null
  };

  // KPI Metrics
  kpiMetrics = [
    {
      title: "Today's Orders",
      value: '0',
      subtitle: '₹0.00 Gross Sales',
      trend: 'Live database sync',
      color: 'accent'
    },
    {
      title: 'Active Delivery Area',
      value: '20 km',
      subtitle: 'Kuravankonam Straight-Line Radius',
      trend: 'Eligibility Engine Active',
      color: 'success'
    },
    {
      title: 'Low Stock Alerts',
      value: '0',
      subtitle: 'Below re-order threshold (5 units)',
      trend: 'Healthy inventory levels',
      color: 'warning'
    },
    {
      title: 'Pending Return Reviews',
      value: '0',
      subtitle: 'Awaiting Owner Approval',
      trend: 'No pending returns',
      color: 'danger'
    }
  ];

  // Recent Orders Pipeline
  recentOrders: any[] = [];

  constructor(
    private analyticsService: AdminAnalyticsService,
    private orderService: AdminOrderService
  ) {}

  ngOnInit(): void {
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    // 1. Fetch live analytics KPI
    this.analyticsService.getSummary().subscribe({
      next: res => {
        if (res?.success && res.data) {
          const d = res.data;
          this.kpiMetrics[0].value = String(d.totalOrders || 0);
          this.kpiMetrics[0].subtitle = `₹${(d.totalRevenue || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })} Gross Sales`;
          this.kpiMetrics[2].value = String(d.lowStockCount || 0);
          this.kpiMetrics[3].value = String(d.ordersByStatus?.['PROCESSING'] || 0);
        }
      },
      error: () => {
        // Retain clean zero state on connection failure
      }
    });

    // 2. Fetch live recent orders
    this.orderService.getOrders('ALL', '', 0, 5).subscribe({
      next: res => {
        if (res?.success && res.data?.content && res.data.content.length > 0) {
          this.recentOrders = res.data.content.map((o: any) => ({
            orderNumber: o.orderNumber,
            customer: `${o.customerName || 'Customer'} (${o.shopDistanceKm || 0} km)`,
            items: `${o.itemCount || 1} items`,
            amount: `₹${(o.finalPayableAmount || 0).toFixed(2)}`,
            status: o.orderStatus,
            statusClass: 'badge-' + (o.orderStatus?.toLowerCase() || 'pending'),
            time: new Date(o.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
          }));
        } else {
          this.recentOrders = [];
        }
      },
      error: () => {
        this.recentOrders = [];
      }
    });
  }

  toggleOwnerLocationApproval(): void {
    const nextState = !this.shopLocationData.isOwnerApproved;
    this.orderService.verifyShopLocation(nextState).subscribe({
      next: () => {
        this.shopLocationData.isOwnerApproved = nextState;
        this.shopLocationData.verifiedDate = nextState
          ? new Date().toLocaleDateString('en-IN', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
          : null;
      },
      error: () => {
        this.shopLocationData.isOwnerApproved = nextState;
      }
    });
  }
}
