import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { BackButtonComponent } from '../../components/back-button/back-button.component';

export type PolicyTab = 'shipping' | 'returns' | 'terms' | 'privacy';

@Component({
  selector: 'app-policies',
  standalone: true,
  imports: [CommonModule, RouterModule, BackButtonComponent],
  templateUrl: './policies.component.html',
  styleUrls: ['./policies.component.scss']
})
export class PoliciesComponent implements OnInit {
  activeTab: PolicyTab = 'shipping';

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const slug = params.get('slug');
      if (slug && this.isValidTab(slug)) {
        this.activeTab = slug as PolicyTab;
      }
    });

    this.route.url.subscribe(segments => {
      const path = segments.map(s => s.path).join('/');
      if (path.includes('shipping')) this.activeTab = 'shipping';
      else if (path.includes('returns')) this.activeTab = 'returns';
      else if (path.includes('terms')) this.activeTab = 'terms';
      else if (path.includes('privacy')) this.activeTab = 'privacy';
    });
  }

  setTab(tab: PolicyTab): void {
    this.activeTab = tab;
  }

  private isValidTab(slug: string): boolean {
    return ['shipping', 'returns', 'terms', 'privacy'].includes(slug);
  }
}
