import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { SupportService, StoreInfo, FaqItem, SupportTicket, CreateInquiryPayload } from '../../core/services/support.service';
import { BackButtonComponent } from '../../components/back-button/back-button.component';

@Component({
  selector: 'app-support',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, BackButtonComponent],
  templateUrl: './support.component.html',
  styleUrls: ['./support.component.scss']
})
export class SupportComponent implements OnInit {
  storeInfo: StoreInfo | null = null;
  faqs: FaqItem[] = [];
  openFaqIndex: number | null = 0; // First FAQ open by default
  whatsappUrl: string = 'https://wa.me/919447100000?text=Hi%2C%20i%20would%20like%20to%20know%20about%20this%20product';
  contactInfo: any = null;

  inquiryForm: CreateInquiryPayload = {
    name: '',
    email: '',
    phone: '',
    orderNumber: '',
    subject: '',
    message: '',
    priority: 'MEDIUM'
  };

  isSubmitting = false;
  submittedTicket: SupportTicket | null = null;
  errorMessage = '';
  successMessage = '';

  // Track Ticket Section
  searchTicketNumber = '';
  isTrackingTicket = false;
  trackedTicket: SupportTicket | null = null;
  ticketSearchError = '';
  replyMessageText = '';
  isSendingReply = false;

  constructor(private supportService: SupportService) {}

  ngOnInit(): void {
    this.supportService.getStoreInfo().subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.storeInfo = res.data;
        }
      }
    });

    this.supportService.getFaqs().subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.faqs = res.data;
        }
      }
    });

    this.supportService.getContactInfo().subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.contactInfo = res.data;
          if (res.data.whatsappUrl) {
            this.whatsappUrl = res.data.whatsappUrl;
          }
        }
      }
    });

    this.supportService.getWhatsAppConfig().subscribe({
      next: res => {
        if (res?.success && res.data?.whatsappUrl) {
          this.whatsappUrl = res.data.whatsappUrl;
        }
      }
    });
  }

  toggleFaq(index: number): void {
    this.openFaqIndex = this.openFaqIndex === index ? null : index;
  }

  submitInquiry(): void {
    if (!this.inquiryForm.name.trim() || !this.inquiryForm.email.trim() || !this.inquiryForm.message.trim()) {
      this.errorMessage = 'Please complete all required fields (Name, Email, Message).';
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.supportService.submitInquiry(this.inquiryForm).subscribe({
      next: res => {
        this.isSubmitting = false;
        if (res?.success && res.data) {
          this.submittedTicket = res.data;
          this.successMessage = `Inquiry received! Your ticket number is #${res.data.ticketNumber}. Our team will get back to you shortly.`;
          this.inquiryForm = {
            name: '',
            email: '',
            phone: '',
            orderNumber: '',
            subject: '',
            message: '',
            priority: 'MEDIUM'
          };
        }
      },
      error: err => {
        this.isSubmitting = false;
        this.errorMessage = err.error?.message || 'Failed to submit inquiry. Please call our store directly.';
      }
    });
  }

  trackTicket(): void {
    const tkt = this.searchTicketNumber.trim().toUpperCase();
    if (!tkt) {
      this.ticketSearchError = 'Please enter a ticket number';
      return;
    }

    this.isTrackingTicket = true;
    this.ticketSearchError = '';
    this.trackedTicket = null;

    this.supportService.getTicket(tkt).subscribe({
      next: res => {
        this.isTrackingTicket = false;
        if (res?.success && res.data) {
          this.trackedTicket = res.data;
        }
      },
      error: err => {
        this.isTrackingTicket = false;
        this.ticketSearchError = err.error?.message || 'Ticket not found. Please verify the ticket reference number.';
      }
    });
  }

  sendTicketReply(): void {
    if (!this.trackedTicket || !this.replyMessageText.trim()) return;

    this.isSendingReply = true;
    this.supportService.replyTicket(this.trackedTicket.ticketNumber, this.replyMessageText.trim()).subscribe({
      next: res => {
        this.isSendingReply = false;
        this.replyMessageText = '';
        if (res?.success && res.data) {
          this.trackedTicket = res.data;
        }
      },
      error: () => {
        this.isSendingReply = false;
      }
    });
  }
}
