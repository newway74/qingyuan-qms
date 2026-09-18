import { get, post } from './request';
import type { LoginResponse, UserInfo } from '@/types/auth';

export interface LoginPayload {
  username: string;
  password: string;
}

export const authApi = {
  login: (data: LoginPayload) => post<LoginResponse>('/auth/login', data),
  logout: () => post<void>('/auth/logout'),
  userInfo: () => get<UserInfo>('/auth/userinfo'),
  verifyPassword: (password: string) => post<void>('/auth/verify-password', { password }),
};
