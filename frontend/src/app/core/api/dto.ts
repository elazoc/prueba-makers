/**
 * Contrato con el backend (diseño del backend §7, §11.2 y §12; plan del backend, tareas 9 y 10).
 * Si el backend cambia un nombre, se cambia aquí y en los `*.api.ts`.
 */

export type Role = 'USER' | 'ADMIN';
export type LoanStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface UserDto {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  active: boolean;
  createdAt: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  /** Segundos de vida del token de acceso. */
  expiresIn: number;
  user: UserDto;
}

export interface LoanDto {
  id: string;
  applicantId: string;
  /** Texto decimal con 2 decimales: "1500000.00". */
  amount: string;
  termMonths: number;
  status: LoanStatus;
  requestedAt: string;
  decidedAt: string | null;
  rejectionReason: string | null;
}

export interface AdminLoanDto extends LoanDto {
  applicantEmail: string;
  applicantName: string;
}

export interface PageDto<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type LoanEventType =
  | 'loan.requested'
  | 'loan.updated'
  | 'loan.cancelled'
  | 'loan.approved'
  | 'loan.rejected';

export interface LoanEventDto {
  eventId: string;
  type: LoanEventType;
  loanId: string;
  applicantId: string;
  status: LoanStatus;
  occurredAt: string;
}

export interface LoanRequest {
  amount: string;
  termMonths: number;
}

export interface RejectRequest {
  reason: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface CreateUserRequest {
  email: string;
  password: string;
  fullName: string;
  role: Role;
}

export interface UpdateUserRequest {
  fullName: string;
  role: Role;
  active: boolean;
}

/** Cuerpo de error RFC 9457 que devuelve el backend. */
export interface ProblemDto {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  code?: string;
  timestamp?: string;
  errorId?: string;
  errors?: { field: string; message: string }[];
}
