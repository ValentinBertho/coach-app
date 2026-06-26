export type UserRole = 'PLATFORM_ADMIN' | 'HEAD_COACH' | 'COACH' | 'ATHLETE';

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  clubId: string | null;
  clubName: string | null;
  emailVerified?: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  clubName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}
