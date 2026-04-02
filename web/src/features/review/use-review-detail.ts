import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { reviewApi } from '@/api/client'
import type {
  CreateReviewCommentInput,
  CreateReviewCommentThreadInput,
  CreateReviewTestRunInput,
  ReviewComment,
  ReviewCommentThread,
  ReviewSkillDetail,
  ReviewTask,
  ReviewTestRun,
  ReviewVersionSnapshot,
} from '@/api/types'

/**
 * Fetches one review task for governance detail views.
 */
async function getReviewDetail(taskId: number): Promise<ReviewTask> {
  return reviewApi.get(taskId)
}

async function getReviewSkillDetail(taskId: number): Promise<ReviewSkillDetail> {
  return reviewApi.getSkillDetail(taskId)
}

async function getReviewVersionSnapshot(taskId: number, versionId: number): Promise<ReviewVersionSnapshot> {
  return reviewApi.getVersionSnapshot(taskId, versionId)
}

async function getReviewCommentThreads(taskId: number, versionId: number, filePath: string): Promise<ReviewCommentThread[]> {
  return reviewApi.listCommentThreads(taskId, versionId, filePath)
}

async function createReviewCommentThread(taskId: number, versionId: number, input: CreateReviewCommentThreadInput): Promise<ReviewCommentThread> {
  return reviewApi.createCommentThread(taskId, versionId, input)
}

async function createReviewComment(taskId: number, threadId: number, input: CreateReviewCommentInput): Promise<ReviewComment> {
  return reviewApi.replyComment(taskId, threadId, input)
}

async function getReviewTestRuns(taskId: number, versionId: number): Promise<ReviewTestRun[]> {
  return reviewApi.listTestRuns(taskId, versionId)
}

async function createReviewTestRun(taskId: number, versionId: number, input: CreateReviewTestRunInput): Promise<ReviewTestRun> {
  return reviewApi.createTestRun(taskId, versionId, input)
}

/**
 * Approves the current review task.
 */
async function approveReview(taskId: number, comment?: string): Promise<void> {
  await reviewApi.approve(taskId, comment)
}

/**
 * Rejects the current review task. A comment is required here because the UI
 * treats rejection as an explicit feedback action rather than a silent deny.
 */
async function rejectReview(taskId: number, comment: string): Promise<void> {
  await reviewApi.reject(taskId, comment)
}

/**
 * Exposes the review detail query keyed by task id.
 */
export function useReviewDetail(taskId: number) {
  return useQuery({
    queryKey: ['reviews', taskId],
    queryFn: () => getReviewDetail(taskId),
    enabled: !!taskId,
  })
}

export function useReviewSkillDetail(taskId: number) {
  return useQuery({
    queryKey: ['reviews', taskId, 'skill-detail'],
    queryFn: () => getReviewSkillDetail(taskId),
    enabled: !!taskId,
  })
}

export function useReviewVersionSnapshot(taskId: number, versionId?: number | null, enabled = true) {
  return useQuery({
    queryKey: ['reviews', taskId, 'versions', versionId],
    queryFn: () => getReviewVersionSnapshot(taskId, Number(versionId)),
    enabled: !!taskId && !!versionId && enabled,
  })
}

export function useReviewCommentThreads(taskId: number, versionId?: number | null, filePath?: string | null, enabled = true) {
  return useQuery({
    queryKey: ['reviews', taskId, 'versions', versionId, 'comments', filePath],
    queryFn: () => getReviewCommentThreads(taskId, Number(versionId), String(filePath)),
    enabled: !!taskId && !!versionId && !!filePath && enabled,
  })
}

export function useReviewTestRuns(taskId: number, versionId?: number | null, enabled = true) {
  return useQuery({
    queryKey: ['reviews', taskId, 'versions', versionId, 'test-runs'],
    queryFn: () => getReviewTestRuns(taskId, Number(versionId)),
    enabled: !!taskId && !!versionId && enabled,
  })
}

export function useCreateReviewCommentThread(callbacks?: { onSuccess?: () => void; onError?: (error: Error) => void }) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ taskId, versionId, input }: { taskId: number; versionId: number; input: CreateReviewCommentThreadInput }) =>
      createReviewCommentThread(taskId, versionId, input),
    onSuccess: (_result, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['reviews', variables.taskId, 'versions', variables.versionId, 'comments', variables.input.filePath],
      })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}

export function useCreateReviewComment(callbacks?: {
  onSuccess?: () => void
  onError?: (error: Error) => void
}) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (variables: {
      taskId: number
      versionId: number
      filePath: string
      threadId: number
      input: CreateReviewCommentInput
    }) => createReviewComment(variables.taskId, variables.threadId, variables.input),
    onSuccess: (_result, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['reviews', variables.taskId, 'versions', variables.versionId, 'comments', variables.filePath],
      })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}

export function useCreateReviewTestRun(callbacks?: { onSuccess?: () => void; onError?: (error: Error) => void }) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ taskId, versionId, input }: { taskId: number; versionId: number; input: CreateReviewTestRunInput }) =>
      createReviewTestRun(taskId, versionId, input),
    onSuccess: (_result, variables) => {
      queryClient.invalidateQueries({ queryKey: ['reviews', variables.taskId, 'versions', variables.versionId, 'test-runs'] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}

/**
 * Approves a review and refreshes both the review queue and the governance
 * dashboard, which reads aggregate review state from separate endpoints.
 */
export function useApproveReview(callbacks?: { onSuccess?: () => void; onError?: (error: Error) => void }) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ taskId, comment }: { taskId: number; comment?: string }) =>
      approveReview(taskId, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
      queryClient.invalidateQueries({ queryKey: ['governance'] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}

/**
 * Rejects a review with the same cache invalidation strategy as approval.
 */
export function useRejectReview(callbacks?: { onSuccess?: () => void; onError?: (error: Error) => void }) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ taskId, comment }: { taskId: number; comment: string }) =>
      rejectReview(taskId, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews'] })
      queryClient.invalidateQueries({ queryKey: ['governance'] })
      callbacks?.onSuccess?.()
    },
    onError: callbacks?.onError,
  })
}
