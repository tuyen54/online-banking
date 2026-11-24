export interface AuditLog {
  id: number;
  eventType: string;
  userId?: string;
  username?: string;
  httpMethod: string;
  requestUri: string;
  statusCode: number;
  remoteIpAddress?: string;
  description?: string;
  timestamp: string;
}

