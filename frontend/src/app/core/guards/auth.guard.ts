import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  // Attempt silent refresh before redirecting if we might have an active refresh cookie
  return authService.refreshToken().pipe(
    map(res => {
      if (authService.isAuthenticated()) {
        return true;
      }
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }),
    catchError(() => of(router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } })))
  );
};
