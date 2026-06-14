import { create } from 'zustand'
import { apiClient } from '@/lib/axiosInstance'
import type { NotificationDto, Page } from '@/types'

interface NotificationState {
  notifications: NotificationDto[]
  totalElements: number
  totalPages: number
  unreadCount: number
  isLoading: boolean
  error: string | null

  fetchUnread: (page?: number, size?: number) => Promise<void>
  fetchAll: (page?: number, size?: number) => Promise<void>
  fetchUnreadCount: () => Promise<void>
  markAsRead: (id: number) => Promise<void>
  markAllAsRead: () => Promise<void>
  clearError: () => void
}

export const useNotificationStore = create<NotificationState>((set, get) => ({
  notifications: [],
  totalElements: 0,
  totalPages: 0,
  unreadCount: 0,
  isLoading: false,
  error: null,

  clearError: () => set({ error: null }),

  fetchUnread: async (page = 0, size = 20) => {
    set({ isLoading: true, error: null })
    try {
      const { data } = await apiClient.get<
        { success: boolean; payload: Page<NotificationDto> }
      >('/notifications', { params: { page, size } })

      set({
        notifications: data.payload.content,
        totalElements: data.payload.totalElements,
        totalPages: data.payload.totalPages,
        isLoading: false,
      })
      // Refresh badge count after fetch
      get().fetchUnreadCount()
    } catch (err) {
      set({ isLoading: false, error: (err as Error).message })
    }
  },

  fetchAll: async (page = 0, size = 20) => {
    set({ isLoading: true, error: null })
    try {
      const { data } = await apiClient.get<
        { success: boolean; payload: Page<NotificationDto> }
      >('/notifications/all', { params: { page, size } })

      set({
        notifications: data.payload.content,
        totalElements: data.payload.totalElements,
        totalPages: data.payload.totalPages,
        isLoading: false,
      })
    } catch (err) {
      set({ isLoading: false, error: (err as Error).message })
    }
  },

  fetchUnreadCount: async () => {
    try {
      const { data } = await apiClient.get<{
        success: boolean
        payload: { unreadCount: number }
      }>('/notifications/unread-count')

      set({ unreadCount: data.payload.unreadCount })
    } catch {
      // Non-critical — don't update error state
    }
  },

  markAsRead: async (id) => {
    try {
      await apiClient.put(`/notifications/${id}/read`)
      set((state) => ({
        notifications: state.notifications.map((n) =>
          n.id === id ? { ...n, isRead: true } : n,
        ),
        unreadCount: Math.max(0, state.unreadCount - 1),
      }))
    } catch (err) {
      set({ error: (err as Error).message })
    }
  },

  markAllAsRead: async () => {
    try {
      await apiClient.put(`/notifications/read-all`)
      set((state) => ({
        notifications: state.notifications.map((n) => ({ ...n, isRead: true })),
        unreadCount: 0,
      }))
    } catch (err) {
      set({ error: (err as Error).message })
    }
  },
}))
