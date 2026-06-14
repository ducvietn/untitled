import { apiClient } from '@/lib/axiosInstance'
import { downloadFile } from '@/lib/axiosInstance'
import type {
  ApiResponse,
  BottleneckReport,
  ClassSummary,
  GodEyeDashboard,
  GroupProgressDto,
  MemberContributionDto,
  SubmissionDto,
  TeacherOverview,
} from '@/types'

export const teacherService = {
  // ── Overview ──────────────────────────────────────────────────────────────

  getOverview: () =>
    apiClient.get<ApiResponse<TeacherOverview>>('/teacher/overview'),

  getClasses: () =>
    apiClient.get<ApiResponse<ClassSummary[]>>('/teacher/classes'),

  getClassDashboard: (classId: number) =>
    apiClient.get<ApiResponse<GodEyeDashboard>>(`/teacher/classes/${classId}/dashboard`),

  // ── Group data ──────────────────────────────────────────────────────────

  getGroupProgress: (groupId: number) =>
    apiClient.get<ApiResponse<GroupProgressDto>>(`/teacher/groups/${groupId}/progress`),

  getContributions: (groupId: number) =>
    apiClient.get<ApiResponse<Record<number, MemberContributionDto>>>(
      `/teacher/groups/${groupId}/contributions`,
    ),

  getBottlenecks: (groupId: number) =>
    apiClient.get<ApiResponse<BottleneckReport>>(`/teacher/groups/${groupId}/bottlenecks`),

  getAllFiles: (groupId: number) =>
    apiClient.get<ApiResponse<SubmissionDto[]>>(`/teacher/groups/${groupId}/files`),

  // ── Master Override ──────────────────────────────────────────────────────

  forceModifyTask: (
    taskId: number,
    payload: {
      taskName?: string
      description?: string
      deadline?: string
      progress?: number
      status?: string
    },
  ) =>
    apiClient.put(`/teacher/tasks/${taskId}`, payload),

  forceExtendDeadline: (taskId: number, newDeadline: string) =>
    apiClient.patch(`/teacher/tasks/${taskId}/deadline`, { newDeadline }),

  forceReassignTask: (taskId: number, newUserId: number) =>
    apiClient.put(`/teacher/tasks/${taskId}/reassign`, { newUserId }),

  forceDeleteTask: (taskId: number) =>
    apiClient.delete(`/teacher/tasks/${taskId}`),

  forceUnlockTask: (taskId: number) =>
    apiClient.post(`/teacher/tasks/${taskId}/unlock`),

  forceApprove: (submissionId: number) =>
    apiClient.post(`/teacher/submissions/${submissionId}/approve`),

  forceReject: (submissionId: number, reason?: string) =>
    apiClient.post(`/teacher/submissions/${submissionId}/reject`, { reason }),

  // ── Reports ─────────────────────────────────────────────────────────────

  previewReport: (groupId: number) =>
    apiClient.get(`/reports/groups/${groupId}/preview` as any),

  downloadReport: async (groupId: number, groupName = 'Group') => {
    await downloadFile(
      `/reports/groups/${groupId}/export`,
      `${groupName}_Report.xlsx`,
    )
  },
}
