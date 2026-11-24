export interface BankAccount {
  id: string;
  maskedAccountNumber: string;
  accountType: 'CHECKING' | 'SAVINGS';
  accountStatus: 'ACTIVE' | 'LOCKED' | 'FROZEN' | 'CLOSED';
  balance: number;
  currency: string;
  openedAt: string;
}

export interface AccountCreationRequest {
  accountType: 'CHECKING' | 'SAVINGS';
  currency?: string;
}

export interface AdminAccountResponse {
  id: string;
  maskedAccountNumber: string;
  accountType: string;
  accountStatus: string;
  ownerId: string;
  ownerUsername: string;
  ownerBranchCode?: string;
  openedAt: string;
}

export interface AccountStatusChangeRequest {
  reason: string;
}

