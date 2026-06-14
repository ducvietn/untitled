/**
 * Shared TypeScript types that mirror the Spring Boot backend DTOs.
 * Keep these in sync with the backend controllers.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Auth
// ─────────────────────────────────────────────────────────────────────────────

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  name: string
  email: string
  password: string
  role: 'ROLE_STUDENT' | 'ROLE_TEACHER' | 'ROLE_OBSERVER'
  subjectCode?: string // required when role === 'ROLE_TEACHER'
}

export interface AuthResponse {
  token: string
  userId: number
  email: string
  role: string
  subjectCode?: string
}

export interface User {
  userId: number
  email: string
  name: string
  role: string
  subjectCode?: string
  accountStatus: string
}

// ─────────────────────────────────────────────────────────────────────────────
// ApiResponse<T> wrapper (all backend responses)
// ─────────────────────────────────────────────────────────────────────────────

export interface ApiResponse<T> {
  success: boolean
  payload: T
  timestamp: string
}

// ─────────────────────────────────────────────────────────────────────────────
// Notifications  (Feature 14)
// ─────────────────────────────────────────────────────────────────────────────

export type NotificationType = 'APP_ALERT' | 'SYSTEM_WARNING'

export interface NotificationDto {
  id: number
  title: string
  message: string
  type: NotificationType
  isRead: boolean
  createdAt: string // ISO 8601
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number // 0-based
  first: boolean
  last: boolean
  empty: boolean
}

// ─────────────────────────────────────────────────────────────────────────────
// Tasks
// ─────────────────────────────────────────────────────────────────────────────

export type TaskStatus =
  | 'IN_PROGRESS'
  | 'PENDING_REVIEW'
  | 'DONE'
  | 'BLOCKED'

export interface Task {
  id: number
  taskName: string
  description?: string
  progress: number // 0–100
  status: TaskStatus
  deadline: string
  estimatedHours?: number
  assignedTo?: User
  groupId: number
  dependsOnTaskId?: number
  createdAt: string
  updatedAt: string
}

// ─────────────────────────────────────────────────────────────────────────────
// Groups
// ─────────────────────────────────────────────────────────────────────────────

export interface Group {
  id: number
  groupName: string
  subjectCode: string
  subjectName: string
  leader?: User
  members?: User[]
  classId?: number
  className?: string
}

// ─────────────────────────────────────────────────────────────────────────────
// Peer Review  (Feature 10)
// ─────────────────────────────────────────────────────────────────────────────

export interface PeerReviewSubmission {
  revieweeId: number
  groupId: number
  score: number // 1–5
  comment?: string
}

/** Response returned to the reviewer — anonymous (no reviewer identity) */
export interface AnonymousReviewDto {
  id: number
  score: number
  comment?: string
  revieweeName: string // name of person being reviewed
  createdAt: string
}

/** Full detail — returned to ROLE_TEACHER only */
export interface PeerReviewDetailDto {
  id: number
  reviewerId: number
  reviewerName: string
  revieweeId: number
  revieweeName: string
  score: number
  comment?: string
  createdAt: string
}

// ─────────────────────────────────────────────────────────────────────────────
// Teacher — God Eye Dashboard  (Feature 8)
// ─────────────────────────────────────────────────────────────────────────────

export type HealthStatus = 'GREEN' | 'YELLOW' | 'RED'

export interface GroupHealth {
  groupId: number
  groupName: string
  leaderId: number
  leaderName: string
  healthStatus: HealthStatus
  overallProgress: number // 0–100
  overdueTasks: number
  frozenTasks: number
  atRiskTaskIds: number[]
}

export interface GodEyeDashboard {
  classId: number
  className: string
  classCode: string
  subjectCode: string
  semester: string
  totalGroups: number
  atRiskGroups: number
  averageGroupProgress: number
  overdueTasks: number
  groups: GroupHealth[]
}

export interface GroupProgressDto {
  groupId: number
  groupName: string
  overallProgress: number
  taskCount: number
  tasks: Task[]
  lastUpdated: string
}

export interface MemberContributionDto {
  userId: number
  userName: string
  contributionPercent: number // 0–100
  tasksCompleted: number
  tasksTotal: number
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottleneck Detection  (Feature 11)
// ─────────────────────────────────────────────────────────────────────────────

export interface BottleneckTaskDto {
  taskId: number
  taskName: string
  assigneeId: number
  assigneeName: string
  estimatedHours: number
  progress: number
  severityScore: number
  blockedTaskCount: number
  blockedTaskIds: number[]
  reason: string
  deadline: string
  overdue: boolean
}

export interface BottleneckReport {
  groupId: number
  groupName: string
  hasBottlenecks: boolean
  totalBlockingTaskCount: number
  bottlenecks: BottleneckTaskDto[]
}

// ─────────────────────────────────────────────────────────────────────────────
// Group Report  (Feature 8 — Excel Export)
// ─────────────────────────────────────────────────────────────────────────────

export interface GroupReport {
  groupId: number
  groupName: string
  subjectCode: string
  generatedAt: string
  members: MemberContributionDto[]
  tasks: Task[]
  submissions: SubmissionDto[]
}

export interface SubmissionDto {
  id: number
  taskName: string
  uploaderName: string
  submittedAt: string
  status: 'ON_TIME' | 'LATE' | 'MISSING'
}

// ─────────────────────────────────────────────────────────────────────────────
// File Drive  (Features 12 & 13)
// ─────────────────────────────────────────────────────────────────────────────

export interface GroupFileDto {
  submissionId: number
  fileName: string
  fileUrl: string
  contentType?: string
  fileSizeBytes: number
  fileSizeHuman: string
  uploaderId: number
  uploaderName: string
  taskId: number
  taskName: string
  submittedAt: string
  isOwnSubmission: boolean
}

export interface GroupFileListDto {
  groupId: number
  groupName: string
  totalFiles: number
  totalSizeBytes: number
  files: GroupFileDto[]
  summary: {
    uniqueUploaders: number
    uniqueTasks: number
    oldestFile?: string
    newestFile?: string
    lastUploadedAt?: string
  }
}

export interface UploadResultDto {
  submissionId: number
  fileUrl: string
  fileName: string
  fileSizeBytes: number
  contentType?: string
  message: string
}

// ─────────────────────────────────────────────────────────────────────────────
// Teacher — Overview DTOs
// ─────────────────────────────────────────────────────────────────────────────

export interface TeacherOverview {
  teacherId: number
  email: string
  subjectCode: string
  totalClasses: number
  classes: ClassOverviewItem[]
}

export interface ClassOverviewItem {
  classId: number
  className: string
  classCode: string
  subjectCode: string
  semester: string
  totalGroups: number
  atRiskGroups: number
  avgProgress: number
  overdueTasks: number
  atRiskGroupNames: string[]
}

export interface ClassSummary {
  classId: number
  className: string
  classCode: string
  subjectCode: string
  subjectName: string
  semester: string
  groupCount: number
}
