import {
  createBrowserRouter,
  RouterProvider,
  Navigate,
  Outlet,
} from 'react-router-dom'
import { Suspense, lazy, useEffect } from 'react'

import { AuthGuard, TeacherGuard, ObserverGuard } from './guards'
import { MainLayout } from '@/components/layout/MainLayout'
import { useAuthStore } from '@/store/authStore'
import { useNotificationStore } from '@/store/notificationStore'

// ─────────────────────────────────────────────────────────────────────────────
// Lazy pages
// ─────────────────────────────────────────────────────────────────────────────

const LoginPage = lazy(() => import('@/pages/LoginPage'))
const RegisterPage = lazy(() => import('@/pages/RegisterPage'))
const StudentDashboard = lazy(() => import('@/pages/student/DashboardPage'))
const MyTasksPage = lazy(() => import('@/pages/student/MyTasksPage'))
const FileDrivePage = lazy(() => import('@/pages/student/FileDrivePage'))
const PeerReviewPage = lazy(() => import('@/pages/student/PeerReviewPage'))
const TeacherOverviewPage = lazy(() => import('@/pages/teacher/TeacherOverviewPage'))
const TeacherGroupPage = lazy(() => import('@/pages/teacher/TeacherGroupPage'))
const ObserverGroupsPage = lazy(() => import('@/pages/observer/ObserverGroupsPage'))
const NotificationPage = lazy(() => import('@/pages/NotificationPage'))
const UnauthorizedPage = lazy(() => import('@/pages/UnauthorizedPage'))
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'))

// ─────────────────────────────────────────────────────────────────────────────
// Page loader fallback
// ─────────────────────────────────────────────────────────────────────────────

function PageLoader() {
  return (
    <div className="flex items-center justify-center min-h-[60vh]">
      <div className="flex flex-col items-center gap-3">
        <div className="h-8 w-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
        <p className="text-muted-foreground text-sm">Đang tải…</p>
      </div>
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// Root redirect — based on role
// ─────────────────────────────────────────────────────────────────────────────

function RootRedirect() {
  const user = useAuthStore((s) => s.user)
  if (!user) return <Navigate to="/login" replace />
  if (user.role === 'ROLE_TEACHER' || user.role === 'ROLE_ADMIN') {
    return <Navigate to="/teacher" replace />
  }
  if (user.role === 'ROLE_OBSERVER') {
    return <Navigate to="/observer" replace />
  }
  return <Navigate to="/student" replace />
}

// ─────────────────────────────────────────────────────────────────────────────
// Notification bootstrap — lives at root of authenticated layout
// ─────────────────────────────────────────────────────────────────────────────

function NotificationBootstrap() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const fetchUnreadCount = useNotificationStore((s) => s.fetchUnreadCount)

  useEffect(() => {
    if (!isAuthenticated) return
    fetchUnreadCount()
    const id = setInterval(fetchUnreadCount, 60_000)
    return () => clearInterval(id)
  }, [isAuthenticated, fetchUnreadCount])

  return null
}

// ─────────────────────────────────────────────────────────────────────────────
// Router definition
// ─────────────────────────────────────────────────────────────────────────────

export const router = createBrowserRouter([
  // ── Root redirect ──────────────────────────────────────────────────────────
  { path: '/', element: <RootRedirect /> },

  // ── Public ────────────────────────────────────────────────────────────────
  { path: '/login',    element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },

  // ── Authenticated layout (all roles) ─────────────────────────────────────
  {
    element: (
      <>
        <NotificationBootstrap />
        <MainLayout>
          <Suspense fallback={<PageLoader />}>
            <Outlet />
          </Suspense>
        </MainLayout>
      </>
    ),
    children: [
      // Any authenticated user
      {
        element: <AuthGuard />,
        children: [
          { path: '/notifications', element: <NotificationPage /> },
          { path: '/unauthorized', element: <UnauthorizedPage /> },
        ],
      },

      // Student
      {
        element: <AuthGuard />,
        children: [
          { path: '/student',             element: <StudentDashboard /> },
          { path: '/student/tasks',       element: <MyTasksPage /> },
          { path: '/student/files',        element: <FileDrivePage /> },
          { path: '/student/peer-review', element: <PeerReviewPage /> },
        ],
      },

      // Teacher / Admin
      {
        element: <TeacherGuard />,
        children: [
          { path: '/teacher', element: <TeacherOverviewPage /> },
          { path: '/teacher/class/:classId/dashboard', element: <TeacherOverviewPage /> },
          { path: '/teacher/group/:groupId', element: <TeacherGroupPage /> },
        ],
      },

      // Observer
      {
        element: <ObserverGuard />,
        children: [
          { path: '/observer', element: <ObserverGroupsPage /> },
        ],
      },
    ],
  },

  // ── 404 ──────────────────────────────────────────────────────────────────
  { path: '*', element: <NotFoundPage /> },
])

// ─────────────────────────────────────────────────────────────────────────────
// Provider
// ─────────────────────────────────────────────────────────────────────────────

export function AppRouter() {
  return <RouterProvider router={router} />
}
