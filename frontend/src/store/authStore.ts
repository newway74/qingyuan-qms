import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { UserInfo } from '@/types/auth';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserInfo | null;
  setTokens: (accessToken: string, refreshToken: string) => void;
  setUser: (user: UserInfo) => void;
  clearAuth: () => void;
  hasPerm: (perm: string) => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setTokens: (accessToken, refreshToken) => set({ accessToken, refreshToken }),
      setUser: (user) => set({ user }),
      clearAuth: () => set({ accessToken: null, refreshToken: null, user: null }),
      hasPerm: (perm: string) => {
        const perms = get().user?.permissions || [];
        if (perms.includes('*')) return true;
        if (perms.includes(perm)) return true;
        return perms.some((p) => p.endsWith(':*') && perm.startsWith(p.slice(0, -1)));
      },
    }),
    { name: 'qingyuan-qms-auth' },
  ),
);
