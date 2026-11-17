import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { SidebarComponent } from './components/sidebar/sidebar.component';
import { TopbarComponent } from './components/topbar/topbar.component';
import { HealthService } from './core/services/health.service';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, SidebarComponent, TopbarComponent],
  template: `
    <div class="admin-app-container" [class.login-mode]="isLoginPage">
      <ng-container *ngIf="!isLoginPage; else loginTemplate">
        <app-sidebar></app-sidebar>
        <div class="admin-body-area">
          <app-topbar
            [pageTitle]="currentTitle"
            [isBackendOnline]="isBackendOnline"
            (refreshRequested)="checkHealth()"
          ></app-topbar>
          <main class="admin-main-viewport">
            <router-outlet></router-outlet>
          </main>
        </div>
      </ng-container>
      <ng-template #loginTemplate>
        <div class="admin-login-host">
          <router-outlet></router-outlet>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      min-height: 100vh;
      min-height: 100dvh;
    }
    .admin-app-container {
      display: flex;
      min-height: 100vh;
      min-height: 100dvh;
      width: 100%;
      background-color: #F8FAFC;
    }
    .admin-app-container.login-mode {
      display: flex;
      flex-direction: column;
      width: 100%;
      min-height: 100vh;
      min-height: 100dvh;
      background: linear-gradient(135deg, #15221B 0%, #155C3B 100%);
    }
    .admin-login-host {
      display: flex;
      flex-direction: column;
      flex: 1;
      width: 100%;
      min-height: 100vh;
      min-height: 100dvh;
      justify-content: center;
      align-items: center;
    }
    .admin-body-area {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-width: 0;
    }
    .admin-main-viewport {
      padding: 2rem;
      flex: 1;
      overflow-y: auto;
    }
    @media (max-width: 768px) {
      .admin-main-viewport {
        padding: 1rem;
      }
    }
  `]
})
export class AppComponent implements OnInit {
  isLoginPage = typeof window !== 'undefined'
    ? (window.location.pathname.includes('/login') || window.location.pathname === '/' || window.location.pathname === '')
    : false;
  isBackendOnline = false;
  currentTitle = 'Administrative Control Center';

  constructor(
    private router: Router,
    private healthService: HealthService
  ) {
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: any) => {
      const url = event.urlAfterRedirects || event.url || '';
      this.isLoginPage = url.includes('/login') || url === '/' || url === '';
    });
  }

  ngOnInit(): void {
    const currentUrl = this.router.url;
    if (currentUrl.includes('/login') || currentUrl === '/' || currentUrl === '') {
      this.isLoginPage = true;
    }
    this.checkHealth();
  }

  checkHealth(): void {
    this.healthService.checkHealth().subscribe({
      next: (res) => {
        this.isBackendOnline = res.online;
      },
      error: () => {
        this.isBackendOnline = false;
      }
    });
  }
}
