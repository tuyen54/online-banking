import { Injectable } from '@angular/core';
import Keycloak from 'keycloak-js';
import { environment } from '../../../environments/environment';
import { UserRole } from './auth.models';

@Injectable({
  providedIn: 'root'
})
export class KeycloakService {
  private keycloakInstance: Keycloak | null = null;
  private isInitialized = false;

  get keycloak(): Keycloak {
    if (!this.keycloakInstance) {
      this.keycloakInstance = new Keycloak({
        url: environment.keycloak.url,
        realm: environment.keycloak.realm,
        clientId: environment.keycloak.clientId
      });
    }
    return this.keycloakInstance;
  }

  async init(): Promise<boolean> {
    if (this.isInitialized) {
      return this.isAuthenticated();
    }

    try {
      const authenticated = await this.keycloak.init({
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        checkLoginIframe: false
      });
      this.isInitialized = true;
      return authenticated;
    } catch (error) {
      console.error('Keycloak initialization failed:', error);
      this.isInitialized = true;
      return false;
    }
  }

  async login(redirectUri?: string): Promise<void> {
    await this.keycloak.login({
      redirectUri: redirectUri || window.location.origin
    });
  }

  async logout(): Promise<void> {
    await this.keycloak.logout({
      redirectUri: window.location.origin
    });
  }

  isAuthenticated(): boolean {
    return !!this.keycloak.authenticated;
  }

  async getToken(): Promise<string | null> {
    if (!this.isAuthenticated()) {
      return null;
    }
    try {
      // Refresh token if it expires in less than 30 seconds
      await this.keycloak.updateToken(30);
      return this.keycloak.token || null;
    } catch (error) {
      console.warn('Failed to refresh token, prompting login', error);
      await this.login();
      return null;
    }
  }

  getKeycloakUserId(): string | null {
    return this.keycloak.tokenParsed?.sub || null;
  }

  getUsername(): string | null {
    return (this.keycloak.tokenParsed as any)?.preferred_username || null;
  }

  getEmail(): string | null {
    return (this.keycloak.tokenParsed as any)?.email || null;
  }

  getDisplayName(): string | null {
    const parsed = this.keycloak.tokenParsed as any;
    if (parsed?.name) return parsed.name;
    if (parsed?.given_name || parsed?.family_name) {
      return `${parsed.given_name || ''} ${parsed.family_name || ''}`.trim();
    }
    return this.getUsername();
  }

  getRoles(): UserRole[] {
    const realmAccess = (this.keycloak.tokenParsed as any)?.realm_access;
    if (!realmAccess?.roles || !Array.isArray(realmAccess.roles)) {
      return [];
    }
    const recognized: UserRole[] = ['ADMIN', 'STAFF', 'CUSTOMER', 'AUDITOR'];
    return realmAccess.roles
      .map((r: string) => r.toUpperCase() as UserRole)
      .filter((r: UserRole) => recognized.includes(r));
  }

  hasRole(role: UserRole): boolean {
    return this.getRoles().includes(role);
  }

  getPrimaryRole(): UserRole | null {
    const roles = this.getRoles();
    if (roles.includes('ADMIN')) return 'ADMIN';
    if (roles.includes('STAFF')) return 'STAFF';
    if (roles.includes('AUDITOR')) return 'AUDITOR';
    if (roles.includes('CUSTOMER')) return 'CUSTOMER';
    return null;
  }
}

