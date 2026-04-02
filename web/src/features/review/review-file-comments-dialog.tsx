import { useMemo, useState } from 'react'
import { MessageSquarePlus, Reply, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { FileTreeNode } from '@/features/skill/file-tree-builder'
import { canPreviewFile, getFileTypeLabel } from '@/features/skill/file-type-utils'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent } from '@/shared/ui/dialog'
import { Textarea } from '@/shared/ui/textarea'
import { cn } from '@/shared/lib/utils'
import { resolveReviewActionErrorDescription } from './review-error'
import { useCreateReviewComment, useCreateReviewCommentThread, useReviewCommentThreads } from './use-review-detail'

interface ReviewFileCommentsDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  node: FileTreeNode | null
  content: string | null
  isLoading: boolean
  error: Error | null
  onDownload: () => void
  taskId: number
  versionId?: number | null
  canComment: boolean
}

export function ReviewFileCommentsDialog({
  open,
  onOpenChange,
  node,
  content,
  isLoading,
  error,
  onDownload,
  taskId,
  versionId,
  canComment,
}: ReviewFileCommentsDialogProps) {
  const { t, i18n } = useTranslation()
  const [draftLineNumber, setDraftLineNumber] = useState<number | null>(null)
  const [draftBody, setDraftBody] = useState('')
  const [replyThreadId, setReplyThreadId] = useState<number | null>(null)
  const [replyBody, setReplyBody] = useState('')

  const previewCheck = node ? canPreviewFile(node.name, node.file?.fileSize || 0) : { canPreview: false as const }
  const {
    data: threads,
    isLoading: isLoadingThreads,
  } = useReviewCommentThreads(taskId, versionId ?? null, node?.path ?? null, open && !!node && !!versionId && previewCheck.canPreview)
  const createThreadMutation = useCreateReviewCommentThread({
    onSuccess: () => {
      toast.success(t('review.inlineCommentCreateSuccess'))
      setDraftLineNumber(null)
      setDraftBody('')
    },
    onError: (mutationError) => {
      toast.error(t('review.inlineCommentCreateFailed'), resolveReviewActionErrorDescription(mutationError))
    },
  })
  const replyMutation = useCreateReviewComment({
    onSuccess: () => {
      toast.success(t('review.inlineReplyCreateSuccess'))
      setReplyThreadId(null)
      setReplyBody('')
    },
    onError: (mutationError) => {
      toast.error(t('review.inlineReplyCreateFailed'), resolveReviewActionErrorDescription(mutationError))
    },
  })

  const lines = useMemo(() => normalizeContentLines(content), [content])
  const threadsByLine = useMemo(() => {
    const result = new Map<number, typeof threads>()
    for (const thread of threads ?? []) {
      const existing = result.get(thread.lineNumber) ?? []
      result.set(thread.lineNumber, [...existing, thread])
    }
    return result
  }, [threads])

  if (!node) {
    return null
  }

  const handleCreateThread = (lineNumber: number) => {
    if (!versionId) {
      return
    }
    if (!draftBody.trim()) {
      toast.error(t('review.inlineCommentRequired'))
      return
    }

    createThreadMutation.mutate({
      taskId,
      versionId,
      input: {
        filePath: node.path,
        lineNumber,
        body: draftBody.trim(),
      },
    })
  }

  const handleReply = (threadId: number) => {
    if (!versionId) {
      return
    }
    if (!replyBody.trim()) {
      toast.error(t('review.inlineReplyRequired'))
      return
    }

    replyMutation.mutate({
      taskId,
      versionId,
      filePath: node.path,
      threadId,
      input: {
        body: replyBody.trim(),
      },
    })
  }

  const lineCommentCount = threads?.length ?? 0

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        onOpenChange(nextOpen)
        if (!nextOpen) {
          setDraftLineNumber(null)
          setDraftBody('')
          setReplyThreadId(null)
          setReplyBody('')
        }
      }}
    >
      <DialogContent className="w-[min(calc(100vw-2rem),96rem)] max-h-[90vh] p-0 gap-0 flex flex-col [&>button]:hidden">
        <div className="flex items-center justify-between gap-4 border-b border-border/40 bg-muted/30 px-5 py-3">
          <div className="min-w-0 flex-1 space-y-1">
            <div className="flex items-center gap-3 min-w-0">
              <span className="truncate font-mono text-sm font-medium text-foreground">{node.name}</span>
              <span className="rounded border border-border/60 bg-background/60 px-2 py-0.5 text-xs text-muted-foreground">
                {getFileTypeLabel(node.name)}
              </span>
              {versionId ? (
                <span className="rounded border border-primary/30 bg-primary/10 px-2 py-0.5 text-xs font-mono text-primary">
                  v{versionId}
                </span>
              ) : null}
            </div>
            <div className="text-sm text-muted-foreground">
              {t('review.inlineCommentCount', { count: lineCommentCount })}
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={onDownload}>
              {t('filePreview.downloadHint', { name: node.name })}
            </Button>
            <Button variant="ghost" size="icon" onClick={() => onOpenChange(false)} title={t('filePreview.close')}>
              <X className="h-4 w-4" />
            </Button>
          </div>
        </div>

        <div className="grid flex-1 min-h-0 lg:grid-cols-[minmax(0,1fr)_22rem]">
          <div className="overflow-auto bg-card">
            {isLoading ? (
              <div className="flex items-center justify-center py-12">
                <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
              </div>
            ) : error ? (
              <div className="px-6 py-12 text-center">
                <p className="text-sm font-medium text-foreground">{t('filePreview.loadError')}</p>
                <p className="mt-2 text-sm text-muted-foreground">{error.message}</p>
              </div>
            ) : !previewCheck.canPreview ? (
              <div className="px-6 py-12 text-center">
                <p className="text-sm font-medium text-foreground">
                  {previewCheck.reason === 'too-large'
                    ? t('filePreview.tooLarge')
                    : previewCheck.reason === 'binary'
                      ? t('filePreview.binaryFile')
                      : t('filePreview.unsupported')}
                </p>
                <p className="mt-2 text-sm text-muted-foreground">{t('review.inlineCommentsUnavailable')}</p>
              </div>
            ) : (
              <div className="divide-y divide-border/40 border-r border-border/40 bg-background/60">
                {lines.map((line, index) => {
                  const lineNumber = index + 1
                  const lineThreads = threadsByLine.get(lineNumber) ?? []
                  const isDraftOpen = draftLineNumber === lineNumber
                  return (
                    <div key={`${lineNumber}-${line}`} className="space-y-3 px-4 py-2">
                      <div className="grid grid-cols-[3.5rem_minmax(0,1fr)_auto] gap-3">
                        <div className="select-none pt-0.5 text-right font-mono text-xs text-muted-foreground">
                          {lineNumber}
                        </div>
                        <pre className="m-0 whitespace-pre-wrap break-words font-mono text-[13px] leading-6 text-foreground">
                          <code>{line.length > 0 ? line : ' '}</code>
                        </pre>
                        {canComment ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            className="mt-0.5 gap-1 self-start"
                            onClick={() => {
                              setDraftLineNumber((current) => current === lineNumber ? null : lineNumber)
                              setDraftBody('')
                            }}
                          >
                            <MessageSquarePlus className="h-3.5 w-3.5" />
                            {t('review.addLineComment')}
                          </Button>
                        ) : null}
                      </div>

                      {isDraftOpen ? (
                        <div className="ml-[4.25rem] rounded-xl border border-primary/20 bg-primary/5 p-3 space-y-3">
                          <div className="text-sm font-medium text-foreground">
                            {t('review.lineCommentPrompt', { line: String(lineNumber) })}
                          </div>
                          <Textarea
                            rows={4}
                            value={draftBody}
                            placeholder={t('review.lineCommentPlaceholder')}
                            onChange={(event) => setDraftBody(event.target.value)}
                          />
                          <div className="flex gap-2">
                            <Button
                              size="sm"
                              onClick={() => handleCreateThread(lineNumber)}
                              disabled={createThreadMutation.isPending}
                            >
                              {t('review.submitLineComment')}
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => {
                                setDraftLineNumber(null)
                                setDraftBody('')
                              }}
                            >
                              {t('dialog.close')}
                            </Button>
                          </div>
                        </div>
                      ) : null}

                      {lineThreads.map((thread) => (
                        <div key={thread.id} className="ml-[4.25rem] space-y-3 rounded-xl border border-border/70 bg-secondary/15 p-4">
                          {thread.comments.map((comment) => (
                            <div
                              key={comment.id}
                              className={cn(
                                'space-y-2 rounded-lg border border-border/50 bg-background/80 p-3',
                                comment.id === thread.comments[0]?.id && 'border-primary/20'
                              )}
                            >
                              <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
                                <span>{comment.createdBy}</span>
                                <span>{formatLocalDateTime(comment.createdAt, i18n.language)}</span>
                              </div>
                              <p className="whitespace-pre-wrap text-sm leading-6 text-foreground">{comment.body}</p>
                            </div>
                          ))}

                          {canComment ? (
                            replyThreadId === thread.id ? (
                              <div className="space-y-3 rounded-lg border border-border/60 bg-background/70 p-3">
                                <Textarea
                                  rows={3}
                                  value={replyBody}
                                  placeholder={t('review.replyPlaceholder')}
                                  onChange={(event) => setReplyBody(event.target.value)}
                                />
                                <div className="flex gap-2">
                                  <Button
                                    size="sm"
                                    onClick={() => handleReply(thread.id)}
                                    disabled={replyMutation.isPending}
                                  >
                                    {t('review.postReply')}
                                  </Button>
                                  <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => {
                                      setReplyThreadId(null)
                                      setReplyBody('')
                                    }}
                                  >
                                    {t('dialog.close')}
                                  </Button>
                                </div>
                              </div>
                            ) : (
                              <Button
                                variant="outline"
                                size="sm"
                                className="gap-1"
                                onClick={() => {
                                  setReplyThreadId(thread.id)
                                  setReplyBody('')
                                }}
                              >
                                <Reply className="h-3.5 w-3.5" />
                                {t('review.replyToComment')}
                              </Button>
                            )
                          ) : null}
                        </div>
                      ))}
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          <aside className="flex flex-col gap-4 overflow-auto bg-muted/20 px-5 py-5">
            <div className="space-y-2">
              <h3 className="text-sm font-semibold font-heading text-foreground">{t('review.lineCommentsTitle')}</h3>
              <p className="text-sm text-muted-foreground">{t('review.lineCommentsDescription')}</p>
            </div>
            {!canComment ? (
              <div className="rounded-xl border border-border/60 bg-background/70 p-4 text-sm text-muted-foreground">
                {t('review.inlineCommentsReadOnly')}
              </div>
            ) : null}
            <div className="rounded-xl border border-border/60 bg-background/70 p-4 text-sm text-muted-foreground">
              {isLoadingThreads ? t('review.commentsLoading') : t('review.openLineCommentsHint')}
            </div>
            {!isLoadingThreads && lineCommentCount === 0 ? (
              <div className="rounded-xl border border-dashed border-border/70 bg-background/40 p-4 text-sm text-muted-foreground">
                {t('review.noLineComments')}
              </div>
            ) : null}
            {(threads ?? []).slice(0, 8).map((thread) => (
              <div key={thread.id} className="rounded-xl border border-border/60 bg-background/70 p-4 space-y-2">
                <div className="text-xs uppercase tracking-wide text-muted-foreground">
                  {t('review.lineCommentAnchor', { line: String(thread.lineNumber) })}
                </div>
                <div className="text-sm font-medium text-foreground">
                  {thread.comments[0]?.body ?? t('review.noLineComments')}
                </div>
                <div className="text-xs text-muted-foreground">
                  {t('review.inlineCommentReplyCount', { count: Math.max(thread.comments.length - 1, 0) })}
                </div>
              </div>
            ))}
          </aside>
        </div>

        <div className="border-t border-border/40 bg-muted/20 px-5 py-2">
          <span className="font-mono text-xs text-muted-foreground">{node.path}</span>
        </div>
      </DialogContent>
    </Dialog>
  )
}

function normalizeContentLines(content: string | null) {
  if (content == null) {
    return []
  }
  return content.replace(/\r\n/g, '\n').split('\n')
}
