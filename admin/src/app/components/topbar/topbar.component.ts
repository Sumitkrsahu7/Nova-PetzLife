import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AdminUiService } from '../../core/services/admin-ui.service';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './topbar.component.html',
  styleUrls: ['./topbar.component.scss']
})
export class TopbarComponent {
  @Input() pageTitle: string = 'Administrative Dashboard';
  @Input() isBackendOnline: boolean = false;
  @Output() refreshRequested = new EventEmitter<void>();

  constructor(public adminUi: AdminUiService) {}

  onRefresh(): void {
    this.refreshRequested.emit();
  }

  toggleDrawer(): void {
    this.adminUi.toggleDrawer();
  }
}
