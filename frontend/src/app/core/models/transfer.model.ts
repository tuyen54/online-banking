export interface TransferRequest {
  sourceAccountId: string;
  destinationAccountNumber: string;
  amount: number;
  description?: string;
}

export interface TransferResponse {
  transactionReference: string;
  status: string;
  amount: number;
  currency: string;
  sourceAccountNumber: string;
  destinationAccountNumber: string;
  description?: string;
  completedAt: string;
}

export interface TransactionSummary {
  id: string;
  transactionReference: string;
  status: string;
  amount: number;
  currency: string;
  sourceAccountNumber: string;
  destinationAccountNumber: string;
  description?: string;
  timestamp: string;
  direction?: 'DEBIT' | 'CREDIT' | null;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

