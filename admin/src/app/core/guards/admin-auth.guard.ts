import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { of, map, catchError } from 'rxjs';
import { AdminAuthService } from '../services/admin-auth.service';

export const adminAuthGuard: CanActivateFn = (route, state) => {
  const authService = inject(AdminAuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  // If token is missing or expired, attempt seamless session recovery via refresh token
  return authService.refreshToken().pipe(
    map(() => true),
    catchError(() => {
      router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
      return of(false);
    })
  );
};
