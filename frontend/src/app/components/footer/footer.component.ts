import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './footer.component.html',
  styleUrls: ['./footer.component.scss']
})
export class FooterComponent {
  currentYear = new Date().getFullYear();
  shopAddress = 'our store, our Ambalamukku Road, Thiruvananthapuram, Kerala 695003, India';
  googleReviewUrl = 'https://www.google.com/search?q=petzlife+reviews';
}

