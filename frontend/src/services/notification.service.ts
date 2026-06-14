import { apiClient } from '@/lib/axiosInstance'
import type {
  ApiResponse,
  NotificationDto,
  Page,
} from '@/types'

export const notificationService = {
  /**
   * GET /api/notifications
   * Paginated unread notifications, newest first.
   */
  getUnread: (page = 0, size = 20) =>
    apiClient.get<ApiResponse<Page<NotificationDto>>>('/notifications', {
      params: { page, size },
    }),

  /**
   * GET /api/notifications/all
   * Paginated ALL notifications (read + unread), newest first.
   */
  getAll: (page = 0, size = 20) =>
    apiClient.get<ApiResponse<Page<NotificationDto>>>('/notifications/all', {
      params: { page, size },
    }),

  /**
   * GET /api/notifications/unread-count
   * Returns { unreadCount: number }.
   */
  getUnreadCount: () =>
    apiClient.get<ApiResponse<{ unreadCount: number }>>('/notifications/unread-count'),

  /**
   * PUT /api/notifications/{id}/read
   */
  markAsRead: (id: number) =>
    apiClient.put(`/notifications/${id}/read`),

  /**
   * PUT /api/notifications/read-all
   * Returns { markedAsRead: number }.
   */
  markAllAsRead: () =>
    apiClient.put<ApiResponse<{ markedAsRead: number }>>('/notifications/read-all'),
}
