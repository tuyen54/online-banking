import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { KeycloakService } from '../auth/keycloak.service';

export const authGuard: CanActivateFn = async (route, state) => {
  const keycloakService = inject(KeycloakService);

  if (keycloakService.isAuthenticated()) {
    return true;
  }

  // Not authenticated: initiate standard Keycloak login flow
  await keycloakService.login(window.location.origin + state.url);
  return false;
};

