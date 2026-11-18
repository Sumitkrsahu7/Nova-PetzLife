import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from './components/header/header.component';
import { FooterComponent } from './components/footer/footer.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, HeaderComponent, FooterComponent],
  template: `
    <div class="app-layout">
      <app-header
        [cartCount]="cartItemCount"
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
      width: 100%;
      max-width: 100vw;
      overflow-x: hidden;
    }
    .app-main {
      flex: 1;
      width: 100%;
      max-width: 100vw;
      overflow-x: hidden;
    }
  `]
})
export class AppComponent {
  title = 'NOVA PETZLIFE';
  cartItemCount = 0;

  constructor() { }

  handleSearch(query: string): void {
    console.log('Search initiated for:', query);
  }
}
