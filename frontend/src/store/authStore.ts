import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { User } from '@/types'

interface AuthState {
  token: string | null
  user: User | null
  isAuthenticated: boolean

  login: (token: string, user: User) => void
  logout: () => void
  updateUser: (partial: Partial<User>) => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      isAuthenticated: false,

      login: (token, user) =>
        set({ token, user, isAuthenticated: true }),

      logout: () =>
        set({ token: null, user: null, isAuthenticated: false }),

      updateUser: (partial) =>
        set((state) => ({
          user: state.user ? { ...state.user, ...partial } : null,
        })),
    }),
    {
      name: 'teamup_auth',
      // Only persist token and user; skip hydration errors if schema changes
      partialize: (state) => ({ token: state.token, user: state.user }),
    },
  ),
)

// ─────────────────────────────────────────────────────────────────────────────
// Selectors
// ─────────────────────────────────────────────────────────────────────────────

export const selectIsAuthenticated = (s: AuthState) => s.isAuthenticated
export const selectCurrentUser = (s: AuthState) => s.user
export const selectUserRole = (s: AuthState) => s.user?.role
export const selectIsTeacher = (s: AuthState) =>
  s.user?.role === 'ROLE_TEACHER' || s.user?.role === 'ROLE_ADMIN'
export const selectIsAdmin = (s: AuthState) => s.user?.role === 'ROLE_ADMIN'
export const selectIsObserver = (s: AuthState) => s.user?.role === 'ROLE_OBSERVER'
export const selectSubjectCode = (s: AuthState) => s.user?.subjectCode
