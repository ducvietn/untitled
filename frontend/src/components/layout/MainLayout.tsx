import { Link, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import { useNotificationStore } from '@/store/notificationStore'
import { Bell, LogOut, User } from 'lucide-react'
import { cn } from '@/lib/utils'

const NAV_ITEMS_STUDENT = [
  { label: 'Dashboard', to: '/student' },
  { label: 'Nhiệm vụ', to: '/student/tasks' },
  { label: 'File Drive', to: '/student/files' },
  { label: 'Peer Review', to: '/student/peer-review' },
]

const NAV_ITEMS_TEACHER = [
  { label: 'Tổng quan', to: '/teacher' },
]

const NAV_ITEMS_OBSERVER = [
  { label: 'Nhóm tôi quan sát', to: '/observer' },
]

export function MainLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col">
      <Navbar />
      <main className="flex-1">
        <div className="container mx-auto max-w-7xl px-4 py-6">
          {children}
        </div>
      </main>
    </div>
  )
}

function Navbar() {
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const unreadCount = useNotificationStore((s) => s.unreadCount)
  const location = useLocation()

  const navItems =
    user?.role === 'ROLE_TEACHER' || user?.role === 'ROLE_ADMIN'
      ? NAV_ITEMS_TEACHER
      : user?.role === 'ROLE_OBSERVER'
      ? NAV_ITEMS_OBSERVER
      : NAV_ITEMS_STUDENT

  const roleLabel =
    user?.role === 'ROLE_ADMIN'
      ? 'Quản trị viên'
      : user?.role === 'ROLE_TEACHER'
      ? 'Giảng viên'
      : user?.role === 'ROLE_OBSERVER'
      ? 'Người quan sát'
      : 'Sinh viên'

  return (
    <header className="sticky top-0 z-40 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="container mx-auto max-w-7xl px-4">
        <div className="flex h-16 items-center gap-6">
          {/* Logo */}
          <Link to="/" className="flex items-center gap-2 font-bold text-lg text-primary">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
              <User className="h-4 w-4" />
            </div>
            TeamUp
          </Link>

          {/* Nav links */}
          <nav className="flex items-center gap-1">
            {navItems.map((item) => {
              const active = location.pathname === item.to ||
                (item.to !== '/student' && item.to !== '/teacher' && item.to !== '/observer'
                  ? location.pathname.startsWith(item.to)
                  : false)
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  className={cn(
                    'flex items-center gap-1.5 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                    active
                      ? 'bg-primary/10 text-primary'
                      : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                  )}
                >
                  {item.label}
                </Link>
              )
            })}
          </nav>

          {/* Right side */}
          <div className="ml-auto flex items-center gap-3">
            {/* Notification bell */}
            <Link
              to="/notifications"
              relative="route"
              className="relative flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground"
            >
              <Bell className="h-5 w-5" />
              {unreadCount > 0 && (
                <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-[20px] items-center justify-center rounded-full bg-destructive text-[10px] font-bold text-destructive-foreground px-1">
                  {unreadCount > 99 ? '99+' : unreadCount}
                </span>
              )}
            </Link>

            {/* User avatar / name */}
            <div className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-muted text-muted-foreground">
                <span className="text-xs font-bold">{user?.name?.charAt(0).toUpperCase()}</span>
              </div>
              <div className="hidden sm:block">
                <p className="font-medium leading-none">{user?.name}</p>
                <p className="text-xs text-muted-foreground">{roleLabel}</p>
              </div>
            </div>

            {/* Logout */}
            <button
              onClick={logout}
              className="flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-destructive"
              title="Đăng xuất"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </header>
  )
}
