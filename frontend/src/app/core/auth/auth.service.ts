import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { KeycloakService } from './keycloak.service';
import { AuthState, UserRole } from './auth.models';
import { UserProfile } from '../models/user.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private keycloakService = inject(KeycloakService);
  private http = inject(HttpClient);

  private authStateSubject = new BehaviorSubject<AuthState>({
    isAuthenticated: false,
    isInitialized: false,
    keycloakUserId: null,
    username: null,
    email: null,
    displayName: null,
    roles: [],
    primaryRole: null,
    profile: null
  });

  public authState$ = this.authStateSubject.asObservable();

  get currentAuthState(): AuthState {
    return this.authStateSubject.value;
  }

  async initializeAuth(): Promise<boolean> {
    const authenticated = await this.keycloakService.init();
    if (authenticated) {
      const roles = this.keycloakService.getRoles();
      const primaryRole = this.keycloakService.getPrimaryRole();

      this.updateState({
        isAuthenticated: true,
        isInitialized: true,
        keycloakUserId: this.keycloakService.getKeycloakUserId(),
        username: this.keycloakService.getUsername(),
        email: this.keycloakService.getEmail(),
        displayName: this.keycloakService.getDisplayName(),
        roles,
        primaryRole
      });

      // Synchronize backend profile
      await this.loadUserProfile().toPromise();
      return true;
    } else {
      this.updateState({
        isAuthenticated: false,
        isInitialized: true,
        keycloakUserId: null,
        username: null,
        email: null,
        displayName: null,
        roles: [],
        primaryRole: null,
        profile: null
      });
      return false;
    }
  }

  loadUserProfile(): Observable<UserProfile | null> {
    if (!this.keycloakService.isAuthenticated()) {
      return of(null);
    }
    return this.http.get<UserProfile>(`${environment.apiBaseUrl}/api/users/me`).pipe(
      tap((profile) => {
        this.updateState({ profile });
      }),
      catchError((error) => {
        console.warn('Failed to retrieve MySQL UserProfile from backend', error);
        return of(null);
      })
    );
  }

  login(redirectUri?: string): Promise<void> {
    return this.keycloakService.login(redirectUri);
  }

  logout(): Promise<void> {
    return this.keycloakService.logout();
  }

  hasRole(role: UserRole): boolean {
    return this.keycloakService.hasRole(role);
  }

  hasAnyRole(roles: UserRole[]): boolean {
    return roles.some((role) => this.hasRole(role));
  }

  private updateState(partial: Partial<AuthState>): void {
    this.authStateSubject.next({
      ...this.authStateSubject.value,
      ...partial
    });
  }
}

