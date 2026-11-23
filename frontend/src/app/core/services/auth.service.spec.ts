import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AuthService, UserProfile } from './auth.service';
import { environment } from '../../../environments/environment';

describe('AuthService Cross-Tab Synchronization & Logout', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let peerChannel: BroadcastChannel | null = null;
  let adminPeerChannel: BroadcastChannel | null = null;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AuthService]
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);

    if (typeof BroadcastChannel !== 'undefined') {
      try {
        peerChannel = new BroadcastChannel('nova_cust_auth_sync');
        adminPeerChannel = new BroadcastChannel('nova_admin_auth_sync');
      } catch (_) {
        peerChannel = null;
        adminPeerChannel = null;
      }
    }
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
    try {
      peerChannel?.close();
      adminPeerChannel?.close();
    } catch (_) { }
  });

  it('should broadcast LOGOUT on explicit logout and call the logout API when callApi is true', (done) => {
    const mockUser: UserProfile = {
      email: 'test@nova.com',
      firstName: 'Test',
      lastName: 'User',
      roles: ['ROLE_CUSTOMER']
    };
    service.updateCurrentUser(mockUser, 'test-token');

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
    expect(localStorage.getItem('nova_user_token')).toBeNull();
    expect(localStorage.getItem('nova_user_profile')).toBeNull();

    setTimeout(() => {
      if (peerChannel) {
        expect(messageReceived).toBeTrue();
      }
      done();
    }, 50);
  });

  it('should not call the logout API when callApi is false, but still clear state and broadcast', (done) => {
    const mockUser: UserProfile = {
      email: 'test@nova.com',
      firstName: 'Test',
      lastName: 'User',
      roles: ['ROLE_CUSTOMER']
    };
    service.updateCurrentUser(mockUser, 'test-token');

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
    const mockUser: UserProfile = {
      email: 'customer@nova.com',
      firstName: 'Customer',
      lastName: 'Tab',
      roles: ['ROLE_CUSTOMER']
    };
    service.updateCurrentUser(mockUser, 'active-customer-token');

    expect(service.getCurrentUser()).toEqual(mockUser);
    expect(service.getToken()).toBe('active-customer-token');

    let rebroadcastReceived = false;
    if (peerChannel) {
      peerChannel.onmessage = (event: MessageEvent) => {
        if (event.data?.type === 'LOGOUT') {
          rebroadcastReceived = true;
        }
      };
      // Simulate remote tab broadcasting LOGOUT
      peerChannel.postMessage({ type: 'LOGOUT' });
    }

    setTimeout(() => {
      expect(service.getToken()).toBeNull();
      expect(service.getCurrentUser()).toBeNull();
      expect(localStorage.getItem('nova_user_token')).toBeNull();
      expect(localStorage.getItem('nova_user_profile')).toBeNull();

      // No logout API call was triggered by receiving the broadcast
      httpMock.expectNone(`${environment.apiUrl}/auth/logout`);
      // No message loop: peerChannel should not receive its own message back
      expect(rebroadcastReceived).toBeFalse();
      done();
    }, 80);
  });

  it('should isolate customer channel from admin channel events', (done) => {
    const mockUser: UserProfile = {
      email: 'customer@nova.com',
      firstName: 'Customer',
      lastName: 'Isolated',
      roles: ['ROLE_CUSTOMER']
    };
    service.updateCurrentUser(mockUser, 'customer-stay-logged-in');

    if (adminPeerChannel) {
      // Post LOGOUT on the ADMIN channel
      adminPeerChannel.postMessage({ type: 'LOGOUT' });
    }

    setTimeout(() => {
      // Customer state should remain unaffected
      expect(service.getToken()).toBe('customer-stay-logged-in');
      expect(service.getCurrentUser()).toEqual(mockUser);
      httpMock.expectNone(`${environment.apiUrl}/auth/logout`);
      done();
    }, 80);
  });

  it('should function correctly when BroadcastChannel is unavailable', () => {
    const mockUser: UserProfile = {
      email: 'test@nova.com',
      firstName: 'Test',
      lastName: 'User',
      roles: ['ROLE_CUSTOMER']
    };
    service.updateCurrentUser(mockUser, 'test-token');

    // Simulate missing or failed channel
    (service as any).authChannel = null;

    expect(() => service.logout(false)).not.toThrow();
    expect(service.getToken()).toBeNull();
    expect(service.getCurrentUser()).toBeNull();
  });

  it('should detect expired tokens and clean up authentication state', () => {
    // Generate expired JWT (exp in the past)
    const pastExp = Math.floor(Date.now() / 1000) - 3600;
    const expiredPayload = btoa(JSON.stringify({ sub: 'user@example.com', exp: pastExp }));
    const expiredToken = `header.${expiredPayload}.signature`;

    expect(service.isTokenExpired(expiredToken)).toBeTrue();
    expect(service.isTokenExpired(null)).toBeTrue();
    expect(service.isTokenExpired('malformed-token')).toBeTrue();

    // Future token
    const futureExp = Math.floor(Date.now() / 1000) + 3600;
    const futurePayload = btoa(JSON.stringify({ sub: 'user@example.com', exp: futureExp }));
    const validToken = `header.${futurePayload}.signature`;

    expect(service.isTokenExpired(validToken)).toBeFalse();

    // Storing expired token and verifying getCurrentUser() clears false logged-in state
    const mockUser: UserProfile = {
      email: 'expired@nova.com',
      firstName: 'Expired',
      lastName: 'User',
      roles: ['ROLE_CUSTOMER']
    };
    service.updateCurrentUser(mockUser, expiredToken);

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getCurrentUser()).toBeNull();
    expect(localStorage.getItem('nova_user_token')).toBeNull();
    expect(localStorage.getItem('nova_user_profile')).toBeNull();
  });
});
