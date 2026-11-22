import { Injectable, RendererFactory2, Renderer2 } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class AdminUiService {
  private isMobileDrawerOpenSubject = new BehaviorSubject<boolean>(false);
  public isMobileDrawerOpen$ = this.isMobileDrawerOpenSubject.asObservable();

  private isSidebarCollapsedSubject = new BehaviorSubject<boolean>(false);
  public isSidebarCollapsed$ = this.isSidebarCollapsedSubject.asObservable();

  private renderer: Renderer2;

  constructor(
    rendererFactory: RendererFactory2,
    private router: Router
  ) {
    this.renderer = rendererFactory.createRenderer(null, null);

    // Load initial collapse state from localStorage
    if (typeof localStorage !== 'undefined') {
      const saved = localStorage.getItem('nova_admin_sidebar_collapsed');
      if (saved === 'true') {
        this.isSidebarCollapsedSubject.next(true);
      }
    }

    // Automatically close mobile drawer when navigating
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe(() => {
      this.closeDrawer();
    });

    // Close on Escape key press
    if (typeof window !== 'undefined') {
      window.addEventListener('keydown', (e: KeyboardEvent) => {
        if (e.key === 'Escape' && this.isDrawerOpen()) {
          this.closeDrawer();
        }
      });
    }
  }

  public isDrawerOpen(): boolean {
    return this.isMobileDrawerOpenSubject.value;
  }

  public isCollapsed(): boolean {
    return this.isSidebarCollapsedSubject.value;
  }

  public openDrawer(): void {
    this.isMobileDrawerOpenSubject.next(true);
    if (typeof document !== 'undefined') {
      this.renderer.addClass(document.body, 'admin-drawer-open');
    }
  }

  public closeDrawer(): void {
    this.isMobileDrawerOpenSubject.next(false);
    if (typeof document !== 'undefined') {
      this.renderer.removeClass(document.body, 'admin-drawer-open');
    }
  }

  public toggleCollapse(): void {
    const next = !this.isSidebarCollapsedSubject.value;
    this.isSidebarCollapsedSubject.next(next);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('nova_admin_sidebar_collapsed', String(next));
    }
  }

  public toggleDrawer(): void {
    if (typeof window !== 'undefined' && window.innerWidth <= 992) {
      if (this.isDrawerOpen()) {
        this.closeDrawer();
      } else {
        this.openDrawer();
      }
    } else {
      this.toggleCollapse();
    }
  }
}
