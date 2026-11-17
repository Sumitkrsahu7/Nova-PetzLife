import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AdminSupportService, AdminSupportTicket } from '../../core/services/admin-support.service';

@Component({
  selector: 'app-support-tickets',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './support-tickets.component.html',
  styleUrls: ['./support-tickets.component.scss']
})
export class SupportTicketsComponent implements OnInit {
  tickets: AdminSupportTicket[] = [];
  selectedTicket: AdminSupportTicket | null = null;
  activeStatusTab = 'ALL';
  isLoading = false;
  isSendingReply = false;
  replyText = '';
  successMessage = '';
  errorMessage = '';

  statusTabs = ['ALL', 'OPEN', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED'];

  constructor(private supportService: AdminSupportService) {}

  ngOnInit(): void {
    this.fetchTickets();
  }

  fetchTickets(): void {
    this.isLoading = true;
    this.supportService.getTickets(this.activeStatusTab).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data?.content) {
          this.tickets = res.data.content;
        } else {
          this.loadDemoTickets();
        }
      },
      error: () => {
        this.isLoading = false;
        this.loadDemoTickets();
      }
    });
  }

  loadDemoTickets(): void {
    this.tickets = [
      {
        id: 1,
        ticketNumber: 'TKT-20261114-KOW01',
        customerId: 1,
        customerName: 'Anoop Nair',
        customerEmail: 'anoop@example.com',
        customerPhone: '+91 94471 22334',
        orderNumber: 'ORD-20261114-102827',
        subject: 'Delivery time inquiry for Kowdiar address',
        priority: 'MEDIUM',
        status: 'OPEN',
        assignedTo: 'Store Desk',
        messages: [
          {
            id: 1,
            senderType: 'CUSTOMER',
            senderId: 'Anoop Nair',
            message: 'Hi, I live near Kowdiar Palace. Can I expect delivery before 6 PM today?',
            createdAt: '2026-11-14T14:30:00Z'
          }
        ],
        createdAt: '2026-11-14T14:30:00Z',
        updatedAt: '2026-11-14T14:30:00Z'
      }
    ];
  }

  setTab(tab: string): void {
    this.activeStatusTab = tab;
    this.fetchTickets();
  }

  viewTicket(ticket: AdminSupportTicket): void {
    this.supportService.getTicketDetail(ticket.ticketNumber).subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.selectedTicket = res.data;
        } else {
          this.selectedTicket = ticket;
        }
      },
      error: () => {
        this.selectedTicket = ticket;
      }
    });
  }

  closeDrawer(): void {
    this.selectedTicket = null;
    this.replyText = '';
  }

  sendReply(): void {
    if (!this.selectedTicket || !this.replyText.trim()) return;

    this.isSendingReply = true;
    this.supportService.staffReply(this.selectedTicket.ticketNumber, this.replyText.trim()).subscribe({
      next: res => {
        this.isSendingReply = false;
        this.replyText = '';
        if (res?.success && res.data) {
          this.selectedTicket = res.data;
          this.successMessage = 'Reply sent to customer!';
          this.fetchTickets();
          setTimeout(() => this.successMessage = '', 3000);
        }
      },
      error: err => {
        this.isSendingReply = false;
        this.errorMessage = err.error?.message || 'Failed to send reply.';
      }
    });
  }

  updateStatus(status: string): void {
    if (!this.selectedTicket) return;

    this.supportService.updateStatus(this.selectedTicket.id, status).subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.selectedTicket = res.data;
          this.successMessage = `Ticket status updated to ${status}`;
          this.fetchTickets();
          setTimeout(() => this.successMessage = '', 3000);
        }
      }
    });
  }
}
