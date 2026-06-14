import { useEffect, useState } from 'react'
import { formatDistanceToNow } from 'date-fns'
import { vi } from 'date-fns/locale'
import { toast } from 'sonner'
import { CheckCheck, Bell, BellOff, AlertTriangle } from 'lucide-react'
import { useNotificationStore } from '@/store/notificationStore'
import { cn } from '@/lib/utils'
import type { NotificationDto } from '@/types'

export default function NotificationPage() {
  const store = useNotificationStore()
  const [showAll, setShowAll] = useState(false)

  useEffect(() => {
    if (showAll) {
      store.fetchAll()
    } else {
      store.fetchUnread()
    }
  }, [showAll])

  const handleMarkAll = async () => {
    await store.markAllAsRead()
    toast.success('Đã đánh dấu tất cả là đã đọc.')
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Bell className="h-6 w-6" />
            Thông báo
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            {store.unreadCount > 0
              ? `${store.unreadCount} thông báo chưa đọc`
              : 'Tất cả đã được đọc'}
          </p>
        </div>
        {store.unreadCount > 0 && (
          <button
            onClick={handleMarkAll}
            className="flex items-center gap-2 rounded-md border px-3 py-2 text-sm font-medium hover:bg-muted transition-colors"
          >
            <CheckCheck className="h-4 w-4" />
            Đọc tất cả
          </button>
        )}
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b">
        <button
          onClick={() => setShowAll(false)}
          className={cn(
            'px-4 py-2 text-sm font-medium border-b-2 transition-colors -mb-px',
            !showAll
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground',
          )}
        >
          Chưa đọc
          {store.unreadCount > 0 && (
            <span className="ml-1.5 inline-flex h-5 min-w-[20px] items-center justify-center rounded-full bg-destructive text-[10px] font-bold text-destructive-foreground px-1">
              {store.unreadCount}
            </span>
          )}
        </button>
        <button
          onClick={() => setShowAll(true)}
          className={cn(
            'px-4 py-2 text-sm font-medium border-b-2 transition-colors -mb-px',
            showAll
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground',
          )}
        >
          Tất cả
        </button>
      </div>

      {/* List */}
      {store.isLoading ? (
        <div className="flex justify-center py-12">
          <div className="h-8 w-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
        </div>
      ) : store.notifications.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 gap-3 text-muted-foreground">
          <BellOff className="h-12 w-12 opacity-30" />
          <p className="font-medium">Không có thông báo nào</p>
        </div>
      ) : (
        <div className="space-y-2">
          {store.notifications.map((n) => (
            <NotificationItem key={n.id} notification={n} store={store} />
          ))}
        </div>
      )}

      {/* Pagination */}
      {store.totalPages > 1 && (
        <div className="flex justify-center gap-2">
          {Array.from({ length: store.totalPages }, (_, i) => (
            <button
              key={i}
              onClick={() => showAll ? store.fetchAll(i) : store.fetchUnread(i)}
              className={cn(
                'h-8 w-8 rounded-md text-sm font-medium transition-colors',
                i === (store.notifications[0] ? 0 : 0)
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-muted hover:bg-muted/80',
              )}
            >
              {i + 1}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function NotificationItem({
  notification: n,
  store,
}: {
  notification: NotificationDto
  store: typeof useNotificationStore.prototype
}) {
  const isWarning = n.type === 'SYSTEM_WARNING'

  return (
    <div
      className={cn(
        'group relative flex gap-4 rounded-lg border p-4 transition-colors cursor-pointer',
        !n.isRead
          ? isWarning
            ? 'border-warning/50 bg-warning/5'
            : 'border-primary/30 bg-primary/5'
          : 'border-border bg-card hover:bg-muted/50',
      )}
      onClick={() => !n.isRead && store.markAsRead(n.id)}
    >
      {/* Icon */}
      <div
        className={cn(
          'mt-0.5 flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full',
          isWarning
            ? 'bg-warning/20 text-warning'
            : 'bg-primary/20 text-primary',
        )}
      >
        <AlertTriangle className={cn('h-4 w-4', isWarning ? '' : 'hidden')} />
        <Bell className={cn('h-4 w-4', isWarning ? 'hidden' : '')} />
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="flex items-start justify-between gap-2">
          <h3 className={cn('text-sm font-semibold', !n.isRead && 'font-bold')}>
            {n.title}
          </h3>
          {!n.isRead && (
            <span className="mt-0.5 h-2 w-2 flex-shrink-0 rounded-full bg-primary" />
          )}
        </div>
        <p className="mt-1 text-sm text-muted-foreground whitespace-pre-wrap">
          {n.message}
        </p>
        <time className="mt-2 block text-xs text-muted-foreground">
          {formatDistanceToNow(new Date(n.createdAt), {
            addSuffix: true,
            locale: vi,
          })}
        </time>
      </div>
    </div>
  )
}
