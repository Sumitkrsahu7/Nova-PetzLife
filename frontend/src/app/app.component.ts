import { Component, OnInit, OnDestroy } from '@angular/core';
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
      max-width: 100%;
      overflow-x: hidden;
    }
    .app-main {
      flex: 1;
      width: 100%;
      max-width: 100%;
      overflow-x: hidden;
    }
  `]
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'NOVA PETZLIFE';
  cartItemCount = 0;
  private viewportResizeHandler?: () => void;

  constructor() { }

  ngOnInit(): void {
    if (typeof window !== 'undefined' && window.visualViewport) {
      const vv = window.visualViewport;
      this.viewportResizeHandler = () => {
        // Detect software keyboard open state on iOS / mobile viewports
        const isKeyboardOpen = (window.innerHeight - vv.height) > 150;
        document.body.classList.toggle('keyboard-open', isKeyboardOpen);
      };
      vv.addEventListener('resize', this.viewportResizeHandler);
      vv.addEventListener('scroll', this.viewportResizeHandler);
    }
  }

  ngOnDestroy(): void {
    if (typeof window !== 'undefined' && window.visualViewport && this.viewportResizeHandler) {
      window.visualViewport.removeEventListener('resize', this.viewportResizeHandler);
      window.visualViewport.removeEventListener('scroll', this.viewportResizeHandler);
    }
  }

  handleSearch(query: string): void {
    console.log('Search initiated for:', query);
  }
}
