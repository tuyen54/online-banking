import { UserProfile } from '../models/user.model';

export type UserRole = 'ADMIN' | 'STAFF' | 'CUSTOMER' | 'AUDITOR';

export interface AuthState {
  isAuthenticated: boolean;
  isInitialized: boolean;
  keycloakUserId: string | null;
  username: string | null;
  email: string | null;
  displayName: string | null;
  roles: UserRole[];
  primaryRole: UserRole | null;
  profile: UserProfile | null;
}

