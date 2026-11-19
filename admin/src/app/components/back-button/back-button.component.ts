import { Component, Input } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  selector: 'app-back-button',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button type="button" class="btn-admin-back" (click)="goBack()" [attr.aria-label]="label">
      <svg class="back-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <line x1="19" y1="12" x2="5" y2="12"></line>
        <polyline points="12 19 5 12 12 5"></polyline>
      </svg>
      <span class="back-text">{{ label }}</span>
    </button>
  `,
  styles: [`
    :host {
      display: block;
      margin-bottom: 0.85rem;
    }
    .btn-admin-back {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      background: #FFFFFF;
      border: 1px solid #CBD5E1;
      color: #334155;
      padding: 0.4rem 0.9rem;
      border-radius: 6px;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s ease;
      touch-action: manipulation;
      min-height: 38px;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);

      &:hover {
        background: #F8FAFC;
        color: #0F172A;
        border-color: #94A3B8;
        transform: translateX(-2px);
      }
      &:active {
        transform: translateX(0);
      }
      .back-arrow {
        flex-shrink: 0;
        transition: transform 0.15s ease;
      }
      &:hover .back-arrow {
        transform: translateX(-2px);
      }
    }
  `]
})
export class BackButtonComponent {
  @Input() label: string = 'Back';
  @Input() defaultFallback: string = '/dashboard';

  constructor(private location: Location, private router: Router) {}

  goBack(): void {
    if (window.history.length > 1) {
      this.location.back();
    } else {
      this.router.navigateByUrl(this.defaultFallback);
    }
  }
}
