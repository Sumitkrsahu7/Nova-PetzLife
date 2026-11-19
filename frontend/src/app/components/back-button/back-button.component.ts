import { Component, Input } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  selector: 'app-back-button',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button type="button" class="btn-back" (click)="goBack()" [attr.aria-label]="label">
      <span class="back-icon">←</span>
      <span class="back-label">{{ label }}</span>
    </button>
  `,
  styles: [`
    .btn-back {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      background: #FFFFFF;
      border: 1px solid #DDE7DF;
      color: #26352D;
      padding: 0.4rem 0.85rem;
      border-radius: 9999px;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      margin-bottom: 0.85rem;
      transition: all 0.2s ease;
      touch-action: manipulation;
      min-height: 40px;

      &:hover {
        background: #E7F5E9;
        color: #155C3B;
        border-color: #248B58;
      }

      @media (max-width: 768px) {
        min-width: 38px;
        min-height: 38px;
        width: 38px;
        height: 38px;
        padding: 0;
        border-radius: 50%;
        justify-content: center;
        background: #F4F7F5;
        border: 1px solid #DDE7DF;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);

        .back-label {
          display: none !important;
        }

        .back-icon {
          font-size: 1.15rem;
          line-height: 1;
        }
      }
    }
  `]
})
export class BackButtonComponent {
  @Input() label: string = 'Back';
  @Input() defaultFallback: string = '/';

  constructor(private location: Location, private router: Router) {}

  goBack(): void {
    if (window.history.length > 1) {
      this.location.back();
    } else {
      this.router.navigateByUrl(this.defaultFallback);
    }
  }
}
