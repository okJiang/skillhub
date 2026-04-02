import { useMemo, useState } from 'react'
import { useNavigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ChevronDown, Folder } from 'lucide-react'
import type { SkillFile, SkillVersion } from '@/api/types'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Textarea } from '@/shared/ui/textarea'
import { Label } from '@/shared/ui/label'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { toast } from '@/shared/lib/toast'
import { cn } from '@/shared/lib/utils'
import { resolveReviewActionErrorDescription } from '@/features/review/review-error'
import { ReviewSkillDetailSection } from '@/features/review/review-skill-detail-section'
import { ReviewFileCommentsDialog } from '@/features/review/review-file-comments-dialog'
import { ReviewTestRunsSection } from '@/features/review/review-test-runs-section'
import { SecurityAuditSection } from '@/features/security-audit/security-audit-section'
import { FileTree } from '@/features/skill/file-tree'
import type { FileTreeNode } from '@/features/skill/file-tree-builder'
import { useReviewFile } from '@/features/review/use-review-file'
import { buildApiUrl, WEB_API_PREFIX } from '@/api/client'
import {
  useReviewDetail,
  useReviewSkillDetail,
  useApproveReview,
  useRejectReview,
  useReviewVersionSnapshot,
} from '@/features/review/use-review-detail'

function parseMetadataJson(parsed?: string) {
  if (!parsed) {
    return {}
  }
  try {
    const value = JSON.parse(parsed)
    return typeof value === 'object' && value !== null ? value : {}
  } catch {
    return {}
  }
}

function buildMetadataDiffEntries(source?: string, target?: string) {
  const sourceMetadata = parseMetadataJson(source)
  const targetMetadata = parseMetadataJson(target)
  const keys = Array.from(new Set([...Object.keys(sourceMetadata), ...Object.keys(targetMetadata)])).sort()
  return keys
    .filter((key) => JSON.stringify(sourceMetadata[key]) !== JSON.stringify(targetMetadata[key]))
    .map((key) => ({
      key,
      source: sourceMetadata[key],
      target: targetMetadata[key],
    }))
}

function buildFileDiffSummary(sourceFiles?: SkillFile[], targetFiles?: SkillFile[]) {
  const sourceMap = new Map((sourceFiles ?? []).map((file) => [file.filePath, file.sha256]))
  const targetMap = new Map((targetFiles ?? []).map((file) => [file.filePath, file.sha256]))
  const added: string[] = []
  const removed: string[] = []
  const changed: string[] = []

  for (const [path, hash] of sourceMap.entries()) {
    if (!targetMap.has(path)) {
      removed.push(path)
    } else if (targetMap.get(path) !== hash) {
      changed.push(path)
    }
  }
  for (const path of targetMap.keys()) {
    if (!sourceMap.has(path)) {
      added.push(path)
    }
  }

  return { added, removed, changed }
}

function sortRevisionsForReview(versions: SkillVersion[], activeVersion?: string) {
  const weight = (status: string) => {
    if (status === 'PENDING_REVIEW' || status === 'SCANNING' || status === 'SCAN_FAILED') return 0
    if (status === 'REJECTED' || status === 'SUPERSEDED') return 1
    if (status === 'PUBLISHED') return 2
    if (status === 'DRAFT') return 3
    return 4
  }

  return [...versions].sort((left, right) => {
    if (left.version === activeVersion) return -1
    if (right.version === activeVersion) return 1
    const statusDelta = weight(left.status) - weight(right.status)
    if (statusDelta !== 0) return statusDelta
    return right.id - left.id
  })
}

/**
 * Review task detail page for moderators. The route owns the approve/reject
 * interaction state because both actions depend on route-local confirmation
 * dialogs, comment input, and redirect behavior after completion.
 */
export function ReviewDetailPage() {
  const { id } = useParams({ from: '/dashboard/reviews/$id' })
  const navigate = useNavigate()
  const { t, i18n } = useTranslation()
  const taskId = Number(id)

  const { data: review, isLoading } = useReviewDetail(taskId)
  const {
    data: reviewSkillDetail,
    isLoading: isLoadingReviewSkillDetail,
    error: reviewSkillDetailError,
  } = useReviewSkillDetail(taskId)
  const approveMutation = useApproveReview({
    onSuccess: () => {
      toast.success(t('review.approveSuccess'))
      navigate({ to: '/dashboard/reviews' })
    },
    onError: (error) => {
      toast.error(t('review.approveFailed'), resolveReviewActionErrorDescription(error))
    },
  })
  const rejectMutation = useRejectReview({
    onSuccess: () => {
      toast.success(t('review.rejectSuccess'))
      navigate({ to: '/dashboard/reviews' })
    },
    onError: (error) => {
      toast.error(t('review.rejectFailed'), resolveReviewActionErrorDescription(error))
    },
  })

  const [comment, setComment] = useState('')
  const [showRejectForm, setShowRejectForm] = useState(false)
  const [approveDialog, setApproveDialog] = useState(false)
  const [rejectDialog, setRejectDialog] = useState(false)
  const [compareSourceVersionId, setCompareSourceVersionId] = useState<number | null>(null)
  const [compareTargetVersionId, setCompareTargetVersionId] = useState<number | null>(null)
  // File browser sidebar state
  const [fileBrowserOpen, setFileBrowserOpen] = useState(true)
  const [previewNode, setPreviewNode] = useState<FileTreeNode | null>(null)
  const [previewDialogOpen, setPreviewDialogOpen] = useState(false)

  const {
    data: compareSourceSnapshot,
    isLoading: isLoadingCompareSource,
  } = useReviewVersionSnapshot(taskId, compareSourceVersionId, !!compareSourceVersionId)
  const {
    data: compareTargetSnapshot,
    isLoading: isLoadingCompareTarget,
  } = useReviewVersionSnapshot(taskId, compareTargetVersionId, !!compareTargetVersionId)

  // File content for preview — uses the review-bound version via review file API
  const { data: previewContent, isLoading: isLoadingPreview, error: previewError } = useReviewFile(
    taskId,
    previewNode?.path || null,
    previewDialogOpen && !!previewNode
  )

  const handleFileClick = (node: FileTreeNode) => {
    setPreviewNode(node)
    setPreviewDialogOpen(true)
  }

  const handleDownloadFile = () => {
    if (!previewNode) return
    const url = buildApiUrl(`${WEB_API_PREFIX}/reviews/${taskId}/file?path=${encodeURIComponent(previewNode.path)}`)
    const link = document.createElement('a')
    link.href = url
    link.download = previewNode.name
    document.body.appendChild(link)
    link.click()
    link.remove()
  }

  const formatDate = (dateString: string) => {
    return formatLocalDateTime(dateString, i18n.language)
  }

  const handleApprove = async () => {
    approveMutation.mutate({ taskId, comment: comment || undefined })
  }

  const handleReject = async () => {
    if (!comment.trim()) {
      toast.error(t('review.rejectReasonRequired'))
      return
    }
    rejectMutation.mutate({ taskId, comment })
  }

  if (isLoading) {
    return (
      <div className="space-y-6 max-w-3xl animate-fade-up">
        <div className="h-10 w-48 animate-shimmer rounded-lg" />
        <div className="h-64 animate-shimmer rounded-xl" />
      </div>
    )
  }

  if (!review) {
    return (
      <div className="text-center py-20 animate-fade-up">
        <h2 className="text-2xl font-bold font-heading mb-2">{t('review.notFound')}</h2>
      </div>
    )
  }

  const reviewFiles = reviewSkillDetail?.files
  const activeReviewVersion = reviewSkillDetail?.versions?.find(
    (version) => version.version === reviewSkillDetail.activeVersion
  )
  const isApprovalBlockedByScanning = activeReviewVersion?.status === 'SCANNING'
  const revisionHistory = useMemo(
    () => sortRevisionsForReview(reviewSkillDetail?.versions ?? [], reviewSkillDetail?.activeVersion),
    [reviewSkillDetail?.activeVersion, reviewSkillDetail?.versions]
  )
  const metadataDiffEntries = buildMetadataDiffEntries(
    compareSourceSnapshot?.parsedMetadataJson,
    compareTargetSnapshot?.parsedMetadataJson
  )
  const fileDiffSummary = buildFileDiffSummary(compareSourceSnapshot?.files, compareTargetSnapshot?.files)
  const isCompareLoading = isLoadingCompareSource || isLoadingCompareTarget
  const isReadmeChanged = compareSourceSnapshot?.documentationContent !== compareTargetSnapshot?.documentationContent
  const resolveRevisionStatusLabel = (status: string) => {
    if (status === 'DRAFT') return t('skillDetail.versionStatusDraft')
    if (status === 'SCANNING') return t('skillDetail.versionStatusScanning')
    if (status === 'SCAN_FAILED') return t('skillDetail.versionStatusScanFailed')
    if (status === 'PENDING_REVIEW') return t('skillDetail.versionStatusPendingReview')
    if (status === 'PUBLISHED') return t('skillDetail.versionStatusPublished')
    if (status === 'REJECTED') return t('skillDetail.versionStatusRejected')
    if (status === 'SUPERSEDED') return t('skillDetail.versionStatusSuperseded')
    if (status === 'YANKED') return t('skillDetail.versionStatusYanked')
    return status
  }

  const handleOpenRevisionDiff = (sourceVersionId: number) => {
    if (!activeReviewVersion) {
      return
    }
    setCompareSourceVersionId(sourceVersionId)
    setCompareTargetVersionId(activeReviewVersion.id)
  }

  return (
    <div className="max-w-6xl mx-auto flex flex-col lg:flex-row gap-8 animate-fade-up">
      {/* Main Content */}
      <div className="flex-1 min-w-0 space-y-8">
        <div className="flex items-center justify-between">
        <div>
          <h1 className="text-4xl font-bold font-heading mb-2">{t('review.detail')}</h1>
          <p className="text-muted-foreground">{t('review.id')}: {review.id}</p>
        </div>
        <Button variant="outline" onClick={() => navigate({ to: '/dashboard/reviews' })}>
          {t('review.backToList')}
        </Button>
      </div>

      <Card className="p-8 space-y-6">
        <div className="grid grid-cols-2 gap-6">
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.namespace')}</Label>
            <p className="font-semibold font-mono">{review.namespace}/{review.skillSlug}</p>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.version')}</Label>
            <p className="font-semibold">
              <span className="px-2.5 py-0.5 rounded-full bg-primary/10 text-primary text-sm font-mono">
                {review.version}
              </span>
            </p>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.status')}</Label>
            <p className="font-semibold">
              {review.status === 'PENDING' && (
                <span className="px-2.5 py-0.5 rounded-full bg-amber-500/10 text-amber-400 text-sm">{t('review.statusPending')}</span>
              )}
              {review.status === 'APPROVED' && (
                <span className="px-2.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 text-sm">{t('review.statusApproved')}</span>
              )}
              {review.status === 'REJECTED' && (
                <span className="px-2.5 py-0.5 rounded-full bg-red-500/10 text-red-400 text-sm">{t('review.statusRejected')}</span>
              )}
              {review.status === 'SUPERSEDED' && (
                <span className="px-2.5 py-0.5 rounded-full bg-slate-500/10 text-slate-300 text-sm">{t('review.statusSuperseded')}</span>
              )}
            </p>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.submitter')}</Label>
            <p className="font-semibold">{review.submittedByName || review.submittedBy}</p>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.submitTime')}</Label>
            <p className="font-semibold text-muted-foreground">{formatDate(review.submittedAt)}</p>
          </div>
          {review.reviewedBy && (
            <>
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.reviewer')}</Label>
                <p className="font-semibold">{review.reviewedByName || review.reviewedBy}</p>
              </div>
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.reviewTime')}</Label>
                <p className="font-semibold text-muted-foreground">
                  {review.reviewedAt ? formatDate(review.reviewedAt) : '—'}
                </p>
              </div>
            </>
          )}
        </div>

        {review.reviewComment && (
          <div className="space-y-2">
            <Label className="text-xs text-muted-foreground uppercase tracking-wider">{t('review.reviewComment')}</Label>
            <p className="p-4 bg-secondary/50 rounded-xl text-sm leading-relaxed">{review.reviewComment}</p>
          </div>
        )}
      </Card>

      {review.status === 'PENDING' && (
        <Card className="p-8 space-y-6">
          <h2 className="text-xl font-bold font-heading">{t('review.actions')}</h2>

          <div className="space-y-3">
            <Label htmlFor="comment" className="text-sm font-semibold font-heading">{t('review.commentLabel')}</Label>
            <Textarea
              id="comment"
              placeholder={t('review.commentPlaceholder')}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              rows={4}
            />
          </div>

          <div className="flex gap-3">
            <Button
              onClick={() => {
                if (isApprovalBlockedByScanning) {
                  return
                }
                setApproveDialog(true)
              }}
              disabled={approveMutation.isPending || rejectMutation.isPending || isApprovalBlockedByScanning}
            >
              {t('review.approve')}
            </Button>
            {!showRejectForm ? (
              <Button
                variant="destructive"
                onClick={() => setShowRejectForm(true)}
                disabled={approveMutation.isPending || rejectMutation.isPending}
              >
                {t('review.reject')}
              </Button>
            ) : (
              <>
                <Button
                  variant="destructive"
                  onClick={() => {
                    if (!comment.trim()) {
                      toast.error(t('review.rejectReasonRequired'))
                      return
                    }
                    setRejectDialog(true)
                  }}
                  disabled={approveMutation.isPending || rejectMutation.isPending || !comment.trim()}
                >
                  {t('review.confirmReject')}
                </Button>
                <Button
                  variant="outline"
                  onClick={() => setShowRejectForm(false)}
                  disabled={approveMutation.isPending || rejectMutation.isPending}
                >
                  {t('review.cancelReject')}
                </Button>
              </>
            )}
          </div>

          {isApprovalBlockedByScanning && (
            <p className="text-sm text-muted-foreground">{t('review.approveDisabledScanning')}</p>
          )}

          {showRejectForm && !comment.trim() && (
            <p className="text-sm text-destructive">{t('review.rejectReasonRequired')}</p>
          )}
        </Card>
      )}

      {(() => {
        const skillId = reviewSkillDetail?.skill?.id
        const versionId =
          reviewSkillDetail?.versions?.find((v) => v.version === review.version)?.id ??
          review.skillVersionId
        return skillId && versionId ? (
          <SecurityAuditSection skillId={skillId} versionId={versionId} versionStatus={activeReviewVersion?.status} />
        ) : null
      })()}

      <Card className="p-8 space-y-6">
        <div className="space-y-2">
          <h2 className="text-xl font-bold font-heading">{t('review.revisionHistoryTitle')}</h2>
          <p className="text-sm text-muted-foreground">{t('review.revisionHistoryDescription')}</p>
        </div>

        {revisionHistory.length > 0 ? (
          <div className="space-y-3">
            {revisionHistory.map((version) => {
              const isActiveRevision = version.version === reviewSkillDetail?.activeVersion
              return (
                <div
                  key={version.id}
                  className="flex flex-col gap-3 rounded-2xl border border-border/70 bg-card/70 p-4 md:flex-row md:items-center md:justify-between"
                >
                  <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-semibold font-mono">{version.version}</span>
                      <span className="inline-flex items-center rounded-full border border-border px-2.5 py-0.5 text-xs font-medium text-foreground">
                        {resolveRevisionStatusLabel(version.status)}
                      </span>
                      {isActiveRevision ? (
                        <span className="inline-flex items-center rounded-full bg-brand-gradient px-2.5 py-0.5 text-xs font-medium text-white">
                          {t('review.currentRevision')}
                        </span>
                      ) : null}
                    </div>
                    {version.changelog ? (
                      <p className="text-sm text-muted-foreground">{version.changelog}</p>
                    ) : null}
                    <div className="text-sm text-muted-foreground">{t('skillDetail.fileCount', { count: version.fileCount })}</div>
                  </div>

                  {!isActiveRevision && activeReviewVersion ? (
                    <Button variant="outline" onClick={() => handleOpenRevisionDiff(version.id)}>
                      {t('review.compareWithCurrent')}
                    </Button>
                  ) : null}
                </div>
              )
            })}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">{t('skillDetail.noVersions')}</p>
        )}
      </Card>

      <ReviewTestRunsSection
        taskId={taskId}
        versions={revisionHistory}
        activeVersion={reviewSkillDetail?.activeVersion}
        canCreate={review.status === 'PENDING'}
      />

      <ReviewSkillDetailSection
        detail={reviewSkillDetail}
        isLoading={isLoadingReviewSkillDetail}
        hasError={Boolean(reviewSkillDetailError)}
        reviewId={taskId}
      />

      <ConfirmDialog
        open={approveDialog}
        onOpenChange={setApproveDialog}
        title={t('review.approveTitle')}
        description={t('review.approveDescription')}
        confirmText={t('review.approveConfirm')}
        onConfirm={handleApprove}
      />

      <ConfirmDialog
        open={rejectDialog}
        onOpenChange={setRejectDialog}
        title={t('review.rejectTitle')}
        description={t('review.rejectDescription')}
        confirmText={t('review.rejectConfirm')}
        variant="destructive"
        onConfirm={handleReject}
      />

      <Dialog
        open={!!compareSourceVersionId && !!compareTargetVersionId}
        onOpenChange={(open) => {
          if (!open) {
            setCompareSourceVersionId(null)
            setCompareTargetVersionId(null)
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('skillDetail.compareDialogTitle')}</DialogTitle>
            <DialogDescription>
              {compareSourceSnapshot && compareTargetSnapshot
                ? t('skillDetail.compareDialogDescription', {
                    source: compareSourceSnapshot.version,
                    target: compareTargetSnapshot.version,
                  })
                : ''}
            </DialogDescription>
          </DialogHeader>

          {isCompareLoading ? (
            <div className="space-y-3">
              <div className="h-10 animate-shimmer rounded-lg" />
              <div className="h-24 animate-shimmer rounded-xl" />
              <div className="h-24 animate-shimmer rounded-xl" />
            </div>
          ) : compareSourceSnapshot && compareTargetSnapshot ? (
            <div className="space-y-5">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div className="rounded-lg border border-border/60 p-3">
                  <div className="text-muted-foreground">{t('skillDetail.compareSourceLabel')}</div>
                  <div className="mt-1 font-mono text-foreground">v{compareSourceSnapshot.version}</div>
                </div>
                <div className="rounded-lg border border-border/60 p-3">
                  <div className="text-muted-foreground">{t('skillDetail.compareTargetLabel')}</div>
                  <div className="mt-1 font-mono text-foreground">v{compareTargetSnapshot.version}</div>
                </div>
              </div>

              <div className="space-y-2">
                <div className="text-sm font-semibold text-foreground">{t('skillDetail.metadataChanges')}</div>
                {metadataDiffEntries.length > 0 ? (
                  <div className="space-y-2">
                    {metadataDiffEntries.map((entry) => (
                      <div key={entry.key} className="rounded-lg border border-border/60 p-3 text-sm">
                        <div className="font-medium text-foreground">{entry.key}</div>
                        <div className="mt-1 text-muted-foreground">
                          {String(entry.source ?? '—')} → {String(entry.target ?? '—')}
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="text-sm text-muted-foreground">{t('skillDetail.noMetadataChanges')}</div>
                )}
              </div>

              <div className="space-y-2">
                <div className="text-sm font-semibold text-foreground">{t('skillDetail.readmeChange')}</div>
                <div className="text-sm text-muted-foreground">
                  {isReadmeChanged ? t('skillDetail.readmeChanged') : t('skillDetail.readmeUnchanged')}
                </div>
              </div>

              <div className="space-y-3">
                <div className="text-sm font-semibold text-foreground">{t('skillDetail.fileChanges')}</div>
                <div className="grid grid-cols-3 gap-3 text-sm">
                  <div className="rounded-lg border border-border/60 p-3">
                    <div className="text-muted-foreground">{t('skillDetail.filesAdded')}</div>
                    <div className="mt-1 font-semibold text-foreground">{fileDiffSummary.added.length}</div>
                  </div>
                  <div className="rounded-lg border border-border/60 p-3">
                    <div className="text-muted-foreground">{t('skillDetail.filesRemoved')}</div>
                    <div className="mt-1 font-semibold text-foreground">{fileDiffSummary.removed.length}</div>
                  </div>
                  <div className="rounded-lg border border-border/60 p-3">
                    <div className="text-muted-foreground">{t('skillDetail.filesChanged')}</div>
                    <div className="mt-1 font-semibold text-foreground">{fileDiffSummary.changed.length}</div>
                  </div>
                </div>

                {([['added', fileDiffSummary.added], ['removed', fileDiffSummary.removed], ['changed', fileDiffSummary.changed]] as const)
                  .filter(([, files]) => files.length > 0)
                  .map(([kind, files]) => (
                    <div key={kind} className="rounded-lg border border-border/60 p-3 text-sm">
                      <div className="font-medium text-foreground">
                        {kind === 'added'
                          ? t('skillDetail.filesAdded')
                          : kind === 'removed'
                            ? t('skillDetail.filesRemoved')
                            : t('skillDetail.filesChanged')}
                      </div>
                      <div className="mt-2 flex flex-wrap gap-2">
                        {files.map((file) => (
                          <span key={file} className="rounded-full bg-secondary px-3 py-1 font-mono text-xs text-secondary-foreground">
                            {file}
                          </span>
                        ))}
                      </div>
                    </div>
                  ))}
              </div>
            </div>
          ) : (
            <div className="text-sm text-muted-foreground">{t('review.revisionDiffUnavailable')}</div>
          )}

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setCompareSourceVersionId(null)
                setCompareTargetVersionId(null)
              }}
            >
              {t('dialog.close')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      </div>

      {/* Sidebar — file browser for the review-bound active version */}
      <aside className="w-full lg:w-80 flex-shrink-0 space-y-5">
        {reviewFiles && reviewFiles.length > 0 && (
          <Card className="p-5 space-y-3">
            <button
              type="button"
              className="flex w-full items-center gap-2 text-left"
              aria-expanded={fileBrowserOpen}
              onClick={() => setFileBrowserOpen((v) => !v)}
            >
              <Folder className="w-4 h-4 text-muted-foreground" />
              <span className="text-sm font-semibold font-heading text-foreground">
                {t('fileTree.title')}
              </span>
              <span className="text-xs text-muted-foreground ml-auto mr-2">
                {reviewFiles.length}
              </span>
              <span className={cn(
                'text-muted-foreground transition-transform duration-200',
                fileBrowserOpen && 'rotate-180'
              )}>
                <ChevronDown className="h-4 w-4" />
              </span>
            </button>
            <p className="text-xs text-muted-foreground">{t('review.openLineCommentsHint')}</p>
            {fileBrowserOpen && (
              <div className="max-h-[400px] overflow-y-auto -mx-5 px-5">
                <FileTree files={reviewFiles} onFileClick={handleFileClick} bare />
              </div>
            )}
          </Card>
        )}
        {reviewSkillDetail?.activeVersion && (
          <Card className="p-5 space-y-3">
            <div className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">{t('review.activeReviewVersion')}</span>
              <span className="font-mono font-semibold text-foreground">v{reviewSkillDetail.activeVersion}</span>
            </div>
          </Card>
        )}
      </aside>

      {/* File preview dialog */}
      <ReviewFileCommentsDialog
        open={previewDialogOpen}
        onOpenChange={setPreviewDialogOpen}
        node={previewNode}
        content={previewContent || null}
        isLoading={isLoadingPreview}
        error={previewError}
        onDownload={handleDownloadFile}
        taskId={taskId}
        versionId={activeReviewVersion?.id ?? review.skillVersionId}
        canComment={review.status === 'PENDING'}
      />
    </div>
  )
}
