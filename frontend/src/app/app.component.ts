import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from './components/header/header.component';
import { FooterComponent } from './components/footer/footer.component';
import { HealthService } from './core/services/health.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, HeaderComponent, FooterComponent],
  template: `
    <div class="app-layout">
      <app-header
        [cartCount]="cartItemCount"
        [isBackendOnline]="isBackendOnline"
        (searchSubmitted)="handleSearch($event)"
      ></app-header>
      <main class="app-main">
        <router-outlet></router-outlet>
      </main>
      <app-footer></app-footer>
    </div>
  `,
  styles: [`
    .app-layout {
      display: flex;
      flex-direction: column;
      min-height: 100vh;
    }
    .app-main {
      flex: 1;
    }
  `]
})
export class AppComponent implements OnInit {
  title = 'NOVA PETZLIFE';
  cartItemCount = 0;
  isBackendOnline = false;

  constructor(private healthService: HealthService) {}

  ngOnInit(): void {
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

  handleSearch(query: string): void {
    console.log('Search initiated for:', query);
  }
}
