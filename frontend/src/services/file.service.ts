import { apiClient, downloadFile } from '@/lib/axiosInstance'
import type { ApiResponse, GroupFileListDto } from '@/types'

export const fileService = {
  /**
   * GET /api/groups/{groupId}/files
   * Returns the Group Drive — all files in a group.
   */
  getGroupFiles: (groupId: number) =>
    apiClient.get<ApiResponse<GroupFileListDto>>(`/groups/${groupId}/files`),

  /**
   * GET /api/files/download/{submissionId}
   * Returns { downloadUrl: string } — triggers a browser file download.
   */
  getDownloadUrl: (submissionId: number) =>
    apiClient.get<ApiResponse<{ downloadUrl: string; fileName: string }>>(
      `/files/download/${submissionId}`,
    ),

  /**
   * Triggers a browser file download for the given submission.
   * The backend returns a blob; this helper creates a save-as dialog.
   */
  downloadFile: (submissionId: number, fileName = 'download') =>
    downloadFile(`/files/download/${submissionId}`, fileName),
}
