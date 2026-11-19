import { Component, Input } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  selector: 'app-back-button',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button type="button" class="btn-admin-back" (click)="goBack()" [attr.aria-label]="label">
      <span class="back-arrow">←</span>
      <span class="back-text">{{ label }}</span>
    </button>
  `,
  styles: [`
    .btn-admin-back {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      background: #FFFFFF;
      border: 1px solid #CBD5E1;
      color: #334155;
      padding: 0.4rem 0.85rem;
      border-radius: 6px;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      margin-bottom: 1rem;
      transition: all 0.15s ease;
      touch-action: manipulation;
      min-height: 40px;

      &:hover {
        background: #F1F5F9;
        color: #0F172A;
        border-color: #94A3B8;
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
