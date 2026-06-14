import { apiClient } from '@/lib/axiosInstance'
import type { ApiResponse, AuthResponse, LoginRequest, RegisterRequest, User } from '@/types'

export const authService = {
  login: async (payload: LoginRequest): Promise<AuthResponse> => {
    const { data } = await apiClient.post<ApiResponse<AuthResponse>>('/auth/login', payload)
    return data.payload
  },

  register: async (payload: RegisterRequest): Promise<void> => {
    await apiClient.post('/auth/register', payload)
  },

  me: async (): Promise<User> => {
    const { data } = await apiClient.get<ApiResponse<User>>('/auth/me')
    return data.payload
  },
}
