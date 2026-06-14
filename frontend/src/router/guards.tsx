import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import { useNotificationStore } from '@/store/notificationStore'
import { useEffect } from 'react'

// ─────────────────────────────────────────────────────────────────────────────
// Bootstrap hook — poll unread count when authenticated
// ─────────────────────────────────────────────────────────────────────────────

export function useBootstrapNotifications() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const fetchUnreadCount = useNotificationStore((s) => s.fetchUnreadCount)

  useEffect(() => {
    if (!isAuthenticated) return
    fetchUnreadCount()
    const interval = setInterval(fetchUnreadCount, 60_000)
    return () => clearInterval(interval)
  }, [isAuthenticated, fetchUnreadCount])
}

// ─────────────────────────────────────────────────────────────────────────────
// Route guard components  (render <Outlet /> or redirect)
// ─────────────────────────────────────────────────────────────────────────────

/** Any authenticated user */
export function AuthGuard() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const location = useLocation()
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  return <Outlet />
}

/** ROLE_TEACHER or ROLE_ADMIN only */
export function TeacherGuard() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const isTeacher = useAuthStore((s) =>
    s.user?.role === 'ROLE_TEACHER' || s.user?.role === 'ROLE_ADMIN',
  )
  const location = useLocation()
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  if (!isTeacher) {
    return <Navigate to="/unauthorized" replace />
  }
  return <Outlet />
}

/** ROLE_OBSERVER only */
export function ObserverGuard() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const isObserver = useAuthStore((s) => s.user?.role === 'ROLE_OBSERVER')
  const location = useLocation()
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  if (!isObserver) {
    return <Navigate to="/unauthorized" replace />
  }
  return <Outlet />
}
