export interface Branch {
  id: string;
  branchCode: string;
  branchName: string;
  address?: string;
  status: 'ACTIVE' | 'INACTIVE';
}

export interface UserProfile {
  id: string;
  keycloakUserId: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  profileStatus: 'ACTIVE' | 'SUSPENDED' | 'LOCKED' | 'PENDING_VERIFICATION';
  branch?: Branch;
  identityGroups?: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface CustomerProfile {
  id: string;
  username: string;
  fullName: string;
  email: string;
  profileStatus: string;
  branchCode?: string;
  branchName?: string;
}

