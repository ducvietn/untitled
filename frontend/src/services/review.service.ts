import { apiClient } from '@/lib/axiosInstance'
import type {
  ApiResponse,
  AnonymousReviewDto,
  PeerReviewDetailDto,
  PeerReviewSubmission,
} from '@/types'

export const reviewService = {
  /**
   * POST /api/reviews
   * Submit an anonymous peer review.
   * Returns AnonymousReviewDto (reviewer identity stripped).
   */
  submit: (payload: PeerReviewSubmission) =>
    apiClient.post<ApiResponse<AnonymousReviewDto>>('/reviews', payload),

  /**
   * GET /api/reviews/group/{groupId}/received
   * Returns anonymous reviews received by the current student.
   */
  getReceived: (groupId: number) =>
    apiClient.get<ApiResponse<AnonymousReviewDto[]>>(`/reviews/group/${groupId}/received`),

  /**
   * GET /api/reviews/group/{groupId}
   * Returns FULL detail (reviewer identity visible) — teacher only.
   */
  getAllInGroup: (groupId: number) =>
    apiClient.get<ApiResponse<PeerReviewDetailDto[]>>(`/reviews/group/${groupId}`),
}
