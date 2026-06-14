import axios from 'axios'

/**
 * Centralised Axios instance for all TeamUp API calls.
 *
 * <h3>Base URL</h3>
 * Vite's dev proxy forwards `/api/*` → `http://localhost:8080/api`
 * (see vite.config.ts). In production set VITE_API_BASE_URL to your
 * deployed backend origin (e.g. https://api.teamup.example.com).
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
})

// ─────────────────────────────────────────────────────────────────────────────
// REQUEST INTERCEPTOR — attach JWT
// ─────────────────────────────────────────────────────────────────────────────

apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('teamup_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// ─────────────────────────────────────────────────────────────────────────────
// RESPONSE INTERCEPTOR — handle errors globally
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Maps HTTP status → human-readable message shown to the user.
 * Backend throws ApiResponse-wrapped errors with ErrorDetails inside.
 */
const ERROR_MESSAGES: Record<number, string> = {
  400: 'Yêu cầu không hợp lệ. Vui lòng kiểm tra lại dữ liệu.',
  401: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  403: 'Bạn không có quyền thực hiện thao tác này.',
  404: 'Không tìm thấy tài nguyên yêu cầu.',
  409: 'Xung đột dữ liệu. Vui lòng tải lại trang.',
  413: 'Kích thước tệp vượt quá giới hạn (10 MB).',
  422: 'Dữ liệu không hợp lệ.',
  500: 'Lỗi máy chủ. Vui lòng thử lại sau.',
}

export interface BackendError {
  code?: string
  message: string
  path?: string
  status?: number
}

export interface ApiError {
  message: string
  code: string
  path: string
  httpStatus: number
}

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Network / timeout errors (no response received)
    if (!error.response) {
      const networkError: ApiError = {
        message: 'Không thể kết nối máy chủ. Vui lòng kiểm tra kết nối mạng.',
        code: 'NETWORK_ERROR',
        path: '',
        httpStatus: 0,
      }
      return Promise.reject(networkError)
    }

    const httpStatus = error.response.status

    // Unwrap Spring ApiResponse wrapper when available
    const payload = error.response.data?.payload
    let errorMessage: string
    let errorCode = 'UNKNOWN'

    if (payload && typeof payload === 'object' && 'message' in payload) {
      errorMessage = (payload as BackendError).message ?? ERROR_MESSAGES[httpStatus] ?? error.message
      errorCode = (payload as BackendError).code ?? 'UNKNOWN'
    } else {
      errorMessage = ERROR_MESSAGES[httpStatus] ?? error.message ?? 'Đã xảy ra lỗi.'
    }

    const apiError: ApiError = {
      message: errorMessage,
      code: errorCode,
      path: error.config?.url ?? '',
      httpStatus,
    }

    // 401 — clear token and redirect to login
    if (httpStatus === 401) {
      localStorage.removeItem('teamup_token')
      localStorage.removeItem('teamup_user')
      window.location.href = '/login'
    }

    return Promise.reject(apiError)
  },
)

// ─────────────────────────────────────────────────────────────────────────────
// Blob / file-download helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Downloads a file from the given URL and triggers a browser save-as dialog.
 *
 * @param url        API endpoint that returns a binary blob
 * @param filename   default filename suggested in the save dialog
 * @param apiBaseUrl base URL prefix (defaults to the Axios instance baseURL)
 */
export async function downloadFile(
  url: string,
  filename: string,
  apiBaseUrl = apiClient.defaults.baseURL ?? '',
): Promise<void> {
  const fullUrl = url.startsWith('http') ? url : `${apiBaseUrl}${url}`

  const response = await axios.get(fullUrl, {
    headers: {
      Authorization: `Bearer ${localStorage.getItem('teamup_token')}`,
    },
    responseType: 'blob',
  })

  const blob = new Blob([response.data], {
    type: response.headers['content-type'] as string,
  })

  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(objectUrl)
}
