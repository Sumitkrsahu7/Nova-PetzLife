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
  private focusInHandler?: (e: FocusEvent) => void;
  private focusOutHandler?: (e: FocusEvent) => void;

  constructor() { }

  ngOnInit(): void {
    if (typeof window !== 'undefined') {
      // 1. Instant keyboard detection on input focus/blur (synchronous, iOS Safari + Android compatible)
      this.focusInHandler = (e: FocusEvent) => {
        const target = e.target as HTMLElement | null;
        if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT')) {
          const inputType = (target as HTMLInputElement).type;
          if (inputType !== 'checkbox' && inputType !== 'radio') {
            document.body.classList.add('keyboard-open');
          }
        }
      };

      this.focusOutHandler = () => {
        setTimeout(() => {
          const active = document.activeElement as HTMLElement | null;
          if (!active || !['INPUT', 'TEXTAREA', 'SELECT'].includes(active.tagName)) {
            document.body.classList.remove('keyboard-open');
          }
        }, 80);
      };

      window.addEventListener('focusin', this.focusInHandler);
      window.addEventListener('focusout', this.focusOutHandler);

      // 2. Visual Viewport height change detection (handles accessory bar / virtual keyboard slide)
      if (window.visualViewport) {
        const vv = window.visualViewport;
        this.viewportResizeHandler = () => {
          const isKeyboardOpen = (window.innerHeight - vv.height > 120) ||
                                 (window.screen.height - vv.height > 200);
          if (isKeyboardOpen) {
            document.body.classList.add('keyboard-open');
          } else {
            const active = document.activeElement as HTMLElement | null;
            if (!active || !['INPUT', 'TEXTAREA', 'SELECT'].includes(active.tagName)) {
              document.body.classList.remove('keyboard-open');
            }
          }
        };
        vv.addEventListener('resize', this.viewportResizeHandler);
        vv.addEventListener('scroll', this.viewportResizeHandler);
      }
    }
  }

  ngOnDestroy(): void {
    if (typeof window !== 'undefined') {
      if (this.focusInHandler) window.removeEventListener('focusin', this.focusInHandler);
      if (this.focusOutHandler) window.removeEventListener('focusout', this.focusOutHandler);
      if (window.visualViewport && this.viewportResizeHandler) {
        window.visualViewport.removeEventListener('resize', this.viewportResizeHandler);
        window.visualViewport.removeEventListener('scroll', this.viewportResizeHandler);
      }
    }
  }

  handleSearch(query: string): void {
    console.log('Search initiated for:', query);
  }
}
