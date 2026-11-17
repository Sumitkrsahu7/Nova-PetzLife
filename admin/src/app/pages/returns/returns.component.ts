import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminReturnService, ReturnRequestSummary, InspectPayload } from '../../core/services/admin-return.service';

@Component({
  selector: 'app-returns',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './returns.component.html',
  styleUrls: ['./returns.component.scss']
})
export class ReturnsComponent implements OnInit {
  returnRequests: ReturnRequestSummary[] = [];
  selectedRequest: ReturnRequestSummary | null = null;
  isLoading = false;
  activeTab = 'ALL';
  statusTabs = ['ALL', 'REQUESTED', 'PICKUP_SCHEDULED', 'RECEIVED', 'CLOSED', 'REJECTED'];

  // Inspection Modal State
  showInspectModal = false;
  inspectOutcome = 'APPROVED';
  inspectNotes = '';
  restockStock = true;
  approveRefund = true;
  refundAmount: number = 0;
  isSubmittingInspection = false;

  constructor(private returnService: AdminReturnService) {}

  ngOnInit(): void {
    this.fetchReturns();
  }

  fetchReturns(): void {
    this.isLoading = true;
    this.returnService.getReturnRequests(this.activeTab).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data?.content) {
          this.returnRequests = res.data.content;
        } else {
          this.returnRequests = [];
        }
      },
      error: () => {
        this.isLoading = false;
        this.returnRequests = [];
      }
    });
  }

  setTab(tab: string): void {
    this.activeTab = tab;
    this.fetchReturns();
  }

  viewRequest(req: ReturnRequestSummary): void {
    this.selectedRequest = req;
  }

  closeDetail(): void {
    this.selectedRequest = null;
  }

  schedulePickup(id: number): void {
    this.returnService.schedulePickup(id, 'Store rider pickup dispatched within 20 km zone').subscribe({
      next: () => {
        this.fetchReturns();
        if (this.selectedRequest && this.selectedRequest.id === id) {
          this.selectedRequest.status = 'PICKUP_SCHEDULED';
        }
      }
    });
  }

  markReceived(id: number): void {
    this.returnService.markReceived(id, 'Package brought back to Petzlife Kuravankonam shop').subscribe({
      next: () => {
        this.fetchReturns();
        if (this.selectedRequest && this.selectedRequest.id === id) {
          this.selectedRequest.status = 'RECEIVED';
        }
      }
    });
  }

  openInspectDialog(req: ReturnRequestSummary): void {
    this.selectedRequest = req;
    this.showInspectModal = true;
    this.inspectOutcome = 'APPROVED';
    this.inspectNotes = 'Product received sealed and in undamaged condition. Verified against return policy snapshot.';
    this.restockStock = true;
    this.approveRefund = true;
    this.refundAmount = req.estimatedRefundAmount;
  }

  closeInspectDialog(): void {
    this.showInspectModal = false;
  }

  submitInspection(): void {
    if (!this.selectedRequest) return;
    this.isSubmittingInspection = true;

    const payload: InspectPayload = {
      outcome: this.inspectOutcome,
      conditionNotes: this.inspectNotes,
      restockInventory: this.restockStock,
      approveRefund: this.approveRefund,
      refundAmount: this.refundAmount,
      adminNotes: `Inspected at store on ${new Date().toLocaleDateString()}`
    };

    this.returnService.inspectReturn(this.selectedRequest.id, payload).subscribe({
      next: res => {
        this.isSubmittingInspection = false;
        this.showInspectModal = false;
        this.fetchReturns();
        if (res?.success && res.data) {
          this.selectedRequest = res.data;
        }
      },
      error: err => {
        this.isSubmittingInspection = false;
        alert(err.error?.message || 'Failed to submit inspection decision.');
      }
    });
  }
}
