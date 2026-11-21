import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AdminAuthService, AdminUser } from './admin-auth.service';
import { environment } from '../../../environments/environment';

describe('AdminAuthService Cross-Tab Synchronization & Logout', () => {
  let service: AdminAuthService;
  let httpMock: HttpTestingController;
  let peerChannel: BroadcastChannel | null = null;
  let custPeerChannel: BroadcastChannel | null = null;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AdminAuthService]
    });

    service = TestBed.inject(AdminAuthService);
    httpMock = TestBed.inject(HttpTestingController);

    if (typeof BroadcastChannel !== 'undefined') {
      try {
        peerChannel = new BroadcastChannel('nova_admin_auth_sync');
        custPeerChannel = new BroadcastChannel('nova_cust_auth_sync');
      } catch (_) {
        peerChannel = null;
        custPeerChannel = null;
      }
    }
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
    try {
      peerChannel?.close();
      custPeerChannel?.close();
    } catch (_) { }
  });

  it('should broadcast LOGOUT on explicit logout and call the logout API when callApi is true', (done) => {
    const mockUser: AdminUser = {
      email: 'admin@nova.com',
      firstName: 'Admin',
      lastName: 'User',
      roles: ['ROLE_SUPER_ADMIN']
    };
    (service as any).handleAuthSuccess({
      token: 'admin-token-123',
      tokenType: 'Bearer',
      email: mockUser.email,
      firstName: mockUser.firstName,
      lastName: mockUser.lastName,
      roles: mockUser.roles
    });

    let messageReceived = false;
    if (peerChannel) {
      peerChannel.onmessage = (event: MessageEvent) => {
        if (event.data?.type === 'LOGOUT') {
          messageReceived = true;
        }
      };
    }

    service.logout(true);

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/logout`);
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({ success: true });

    expect(service.getToken()).toBeNull();
    expect(service.getCurrentUser()).toBeNull();
    expect(localStorage.getItem('nova_admin_token')).toBeNull();
    expect(localStorage.getItem('nova_admin_user')).toBeNull();

    setTimeout(() => {
      if (peerChannel) {
        expect(messageReceived).toBeTrue();
      }
      done();
    }, 50);
  });

  it('should not call the logout API when callApi is false, but still clear state and broadcast', (done) => {
    const mockUser: AdminUser = {
      email: 'admin@nova.com',
      firstName: 'Admin',
      lastName: 'User',
      roles: ['ROLE_SUPER_ADMIN']
    };
    (service as any).handleAuthSuccess({
      token: 'admin-token-123',
      tokenType: 'Bearer',
      email: mockUser.email,
      firstName: mockUser.firstName,
      lastName: mockUser.lastName,
      roles: mockUser.roles
    });

    let messageReceived = false;
    if (peerChannel) {
      peerChannel.onmessage = (event: MessageEvent) => {
        if (event.data?.type === 'LOGOUT') {
          messageReceived = true;
        }
      };
    }

    service.logout(false);

    httpMock.expectNone(`${environment.apiUrl}/auth/logout`);
    expect(service.getToken()).toBeNull();
    expect(service.getCurrentUser()).toBeNull();

    setTimeout(() => {
      if (peerChannel) {
        expect(messageReceived).toBeTrue();
      }
      done();
    }, 50);
  });

  it('should clear local storage and reactive state when receiving LOGOUT from another tab without calling API or rebroadcasting', (done) => {
    const mockUser: AdminUser = {
      email: 'admin@nova.com',
      firstName: 'Admin',
      lastName: 'Tab',
      roles: ['ROLE_SUPER_ADMIN']
    };
    (service as any).handleAuthSuccess({
      token: 'active-admin-token',
      tokenType: 'Bearer',
      email: mockUser.email,
      firstName: mockUser.firstName,
      lastName: mockUser.lastName,
      roles: mockUser.roles
    });

    expect(service.getCurrentUser()?.email).toBe('admin@nova.com');
    expect(service.getToken()).toBe('active-admin-token');

    let rebroadcastReceived = false;
    if (peerChannel) {
      peerChannel.onmessage = (event: MessageEvent) => {
        if (event.data?.type === 'LOGOUT') {
          rebroadcastReceived = true;
        }
      };
      // Simulate remote tab broadcasting LOGOUT on admin channel
      peerChannel.postMessage({ type: 'LOGOUT' });
    }

    setTimeout(() => {
      expect(service.getToken()).toBeNull();
      expect(service.getCurrentUser()).toBeNull();
      expect(localStorage.getItem('nova_admin_token')).toBeNull();
      expect(localStorage.getItem('nova_admin_user')).toBeNull();

      httpMock.expectNone(`${environment.apiUrl}/auth/logout`);
      expect(rebroadcastReceived).toBeFalse();
      done();
    }, 80);
  });

  it('should isolate admin channel from customer channel events', (done) => {
    const mockUser: AdminUser = {
      email: 'admin@nova.com',
      firstName: 'Admin',
      lastName: 'Isolated',
      roles: ['ROLE_SUPER_ADMIN']
    };
    (service as any).handleAuthSuccess({
      token: 'admin-stay-logged-in',
      tokenType: 'Bearer',
      email: mockUser.email,
      firstName: mockUser.firstName,
      lastName: mockUser.lastName,
      roles: mockUser.roles
    });

    if (custPeerChannel) {
      // Post LOGOUT on the CUSTOMER channel
      custPeerChannel.postMessage({ type: 'LOGOUT' });
    }

    setTimeout(() => {
      // Admin state should remain unaffected
      expect(service.getToken()).toBe('admin-stay-logged-in');
      expect(service.getCurrentUser()?.email).toBe('admin@nova.com');
      httpMock.expectNone(`${environment.apiUrl}/auth/logout`);
      done();
    }, 80);
  });

  it('should function correctly when BroadcastChannel is unavailable', () => {
    (service as any).handleAuthSuccess({
      token: 'admin-token',
      tokenType: 'Bearer',
      email: 'admin@nova.com',
      firstName: 'Admin',
      lastName: 'User',
      roles: ['ROLE_SUPER_ADMIN']
    });

    // Simulate missing channel
    (service as any).authChannel = null;

    expect(() => service.logout(false)).not.toThrow();
    expect(service.getToken()).toBeNull();
    expect(service.getCurrentUser()).toBeNull();
  });
});
