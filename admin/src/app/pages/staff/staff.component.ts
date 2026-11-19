import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminStaffService, StaffMember, StaffPermission } from '../../core/services/admin-staff.service';
import { AdminAuthService } from '../../core/services/admin-auth.service';

@Component({
  selector: 'app-staff',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './staff.component.html',
  styleUrls: ['./staff.component.scss']
})
export class StaffComponent implements OnInit {
  staffList: StaffMember[] = [];
  availablePermissions: StaffPermission[] = [];
  isLoading = false;
  successMessage = '';
  errorMessage = '';

  // Create Staff Modal
  showCreateModal = false;
  createForm = {
    email: '',
    password: '',
    firstName: '',
    lastName: '',
    phone: '',
    selectedPermissions: {} as { [key: string]: boolean }
  };
  createModalError = '';
  isCreating = false;

  // Edit Permissions Modal
  showPermissionsModal = false;
  editingStaff: StaffMember | null = null;
  permissionsForm: { [key: string]: boolean } = {};
  isUpdatingPermissions = false;
  permissionsModalError = '';

  // Reset Password Modal
  showResetPasswordModal = false;
  resetStaffTarget: StaffMember | null = null;
  newPassword = '';
  isResettingPassword = false;
  resetPasswordError = '';

  constructor(
    private staffService: AdminStaffService,
    public authService: AdminAuthService
  ) {}

  ngOnInit(): void {
    this.fetchStaff();
    this.fetchPermissions();
  }

  fetchStaff(): void {
    this.isLoading = true;
    this.staffService.getStaffMembers().subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data) {
          this.staffList = res.data;
        }
      },
      error: err => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Failed to load staff members. Super Admin permissions required.';
      }
    });
  }

  fetchPermissions(): void {
    this.staffService.getAvailablePermissions().subscribe({
      next: res => {
        if (res?.success && res.data) {
          this.availablePermissions = res.data;
        }
      }
    });
  }

  openCreateModal(): void {
    this.createForm = {
      email: '',
      password: '',
      firstName: '',
      lastName: '',
      phone: '',
      selectedPermissions: {}
    };
    // Default checked permissions for staff
    for (const p of this.availablePermissions) {
      this.createForm.selectedPermissions[p.name] = (p.name === 'ORDER_VIEW' || p.name === 'PRODUCT_VIEW');
    }
    this.createModalError = '';
    this.showCreateModal = true;
  }

  submitCreateStaff(): void {
    if (!this.createForm.email.trim() || !this.createForm.password.trim() || !this.createForm.firstName.trim()) {
      this.createModalError = 'Email, initial password, and first name are required.';
      return;
    }

    const assigned = Object.keys(this.createForm.selectedPermissions)
      .filter(k => this.createForm.selectedPermissions[k]);

    this.isCreating = true;
    this.createModalError = '';

    this.staffService.createStaff({
      email: this.createForm.email.trim(),
      password: this.createForm.password,
      firstName: this.createForm.firstName.trim(),
      lastName: this.createForm.lastName?.trim() || '',
      phone: this.createForm.phone?.trim() || '',
      permissions: assigned
    }).subscribe({
      next: res => {
        this.isCreating = false;
        if (res?.success) {
          this.showCreateModal = false;
          this.fetchStaff();
          this.successMessage = `Staff member ${res.data.email} created successfully!`;
          setTimeout(() => this.successMessage = '', 4000);
        }
      },
      error: err => {
        this.isCreating = false;
        this.createModalError = err.error?.message || 'Failed to create staff member.';
      }
    });
  }

  openPermissionsModal(staff: StaffMember): void {
    this.editingStaff = staff;
    this.permissionsForm = {};
    for (const p of this.availablePermissions) {
      this.permissionsForm[p.name] = staff.permissions?.includes(p.name) || false;
    }
    this.permissionsModalError = '';
    this.showPermissionsModal = true;
  }

  submitPermissionsUpdate(): void {
    if (!this.editingStaff) return;

    const assigned = Object.keys(this.permissionsForm)
      .filter(k => this.permissionsForm[k]);

    this.isUpdatingPermissions = true;
    this.permissionsModalError = '';

    this.staffService.updatePermissions(this.editingStaff.id, assigned).subscribe({
      next: res => {
        this.isUpdatingPermissions = false;
        if (res?.success) {
          this.showPermissionsModal = false;
          this.fetchStaff();
          this.successMessage = `Permissions updated for ${this.editingStaff?.firstName}.`;
          setTimeout(() => this.successMessage = '', 4000);
        }
      },
      error: err => {
        this.isUpdatingPermissions = false;
        this.permissionsModalError = err.error?.message || 'Failed to update permissions.';
      }
    });
  }

  openResetPasswordModal(staff: StaffMember): void {
    this.resetStaffTarget = staff;
    this.newPassword = '';
    this.resetPasswordError = '';
    this.showResetPasswordModal = true;
  }

  submitResetPassword(): void {
    if (!this.resetStaffTarget || !this.newPassword.trim()) {
      this.resetPasswordError = 'Please provide a new secure password.';
      return;
    }

    this.isResettingPassword = true;
    this.resetPasswordError = '';

    this.staffService.resetPassword(this.resetStaffTarget.id, this.newPassword.trim()).subscribe({
      next: res => {
        this.isResettingPassword = false;
        if (res?.success) {
          this.showResetPasswordModal = false;
          this.successMessage = `Password reset successfully for ${this.resetStaffTarget?.email}.`;
          setTimeout(() => this.successMessage = '', 4000);
        }
      },
      error: err => {
        this.isResettingPassword = false;
        this.resetPasswordError = err.error?.message || 'Failed to reset staff password.';
      }
    });
  }

  toggleStatus(staff: StaffMember): void {
    const isSuperAdmin = staff.roles?.includes('ROLE_SUPER_ADMIN');
    if (isSuperAdmin) {
      alert('Super Administrator account cannot be deactivated.');
      return;
    }

    const action = staff.isActive ? 'deactivate' : 'activate';
    if (!confirm(`Are you sure you want to ${action} ${staff.firstName} (${staff.email})?`)) {
      return;
    }

    this.staffService.toggleStaffStatus(staff.id).subscribe({
      next: res => {
        if (res?.success) {
          staff.isActive = !staff.isActive;
          this.successMessage = `Staff account ${action}d successfully.`;
          setTimeout(() => this.successMessage = '', 3000);
        }
      },
      error: err => {
        alert(err.error?.message || `Failed to ${action} staff account.`);
      }
    });
  }
}

type AuthServiceCompat = AdminAuthService;
