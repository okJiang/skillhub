import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { CreateReviewTestRunInput, ReviewTestRunSource, ReviewTestRunStatus, SkillVersion } from '@/api/types'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'
import { cn } from '@/shared/lib/utils'
import { resolveReviewActionErrorDescription } from './review-error'
import { useCreateReviewTestRun, useReviewTestRuns } from './use-review-detail'

const SOURCE_OPTIONS: ReviewTestRunSource[] = ['MANUAL', 'CI', 'SCANNER', 'CUSTOM']
const STATUS_OPTIONS: ReviewTestRunStatus[] = ['PASSED', 'FAILED', 'WARNING', 'INFO']

const INITIAL_FORM_STATE: CreateReviewTestRunInput = {
  source: 'MANUAL',
  status: 'PASSED',
  name: '',
  summary: '',
  detailsMarkdown: '',
  externalUrl: '',
}

interface ReviewTestRunsSectionProps {
  taskId: number
  versions: SkillVersion[]
  activeVersion?: string
  canCreate?: boolean
}

export function ReviewTestRunsSection({ taskId, versions, activeVersion, canCreate = false }: ReviewTestRunsSectionProps) {
  const { t, i18n } = useTranslation()
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [form, setForm] = useState<CreateReviewTestRunInput>(INITIAL_FORM_STATE)

  const activeVersionEntry = versions.find((version) => version.version === activeVersion) ?? versions[0] ?? null
  const effectiveVersionId = selectedVersionId ?? activeVersionEntry?.id ?? null
  const selectedVersion = versions.find((version) => version.id === effectiveVersionId) ?? activeVersionEntry
  const canCreateForSelectedVersion = canCreate && !!effectiveVersionId && effectiveVersionId === activeVersionEntry?.id
  const { data: testRuns, isLoading } = useReviewTestRuns(taskId, effectiveVersionId, !!effectiveVersionId)
  const createTestRunMutation = useCreateReviewTestRun({
    onSuccess: () => {
      toast.success(t('review.testRunCreateSuccess'))
      setDialogOpen(false)
      setForm(INITIAL_FORM_STATE)
    },
    onError: (error) => {
      toast.error(t('review.testRunCreateFailed'), resolveReviewActionErrorDescription(error))
    },
  })

  const handleCreate = () => {
    if (!effectiveVersionId || !canCreateForSelectedVersion) {
      return
    }
    if (!form.name.trim()) {
      toast.error(t('review.testRunNameRequired'))
      return
    }

    createTestRunMutation.mutate({
      taskId,
      versionId: effectiveVersionId,
      input: {
        ...form,
        name: form.name.trim(),
        summary: normalizeOptionalText(form.summary),
        detailsMarkdown: normalizeOptionalText(form.detailsMarkdown),
        externalUrl: normalizeOptionalText(form.externalUrl),
      },
    })
  }

  return (
    <Card className="p-8 space-y-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div className="space-y-2">
          <h2 className="text-xl font-bold font-heading">{t('review.testRunsTitle')}</h2>
          <p className="text-sm text-muted-foreground">{t('review.testRunsDescription')}</p>
        </div>
        {canCreateForSelectedVersion ? (
          <Button variant="outline" onClick={() => setDialogOpen(true)} disabled={!effectiveVersionId}>
            {t('review.addTestRun')}
          </Button>
        ) : null}
      </div>

      {versions.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {versions.map((version) => {
            const isSelected = version.id === effectiveVersionId
            return (
              <button
                key={version.id}
                type="button"
                className={cn(
                  'inline-flex items-center rounded-full border px-3 py-1.5 text-sm font-mono transition-colors',
                  isSelected
                    ? 'border-primary bg-primary/10 text-primary'
                    : 'border-border/70 bg-secondary/30 text-muted-foreground hover:border-primary/40 hover:text-foreground'
                )}
                onClick={() => setSelectedVersionId(version.id)}
              >
                {version.version}
              </button>
            )
          })}
        </div>
      ) : null}

      <div className="rounded-2xl border border-border/70 bg-card/70 p-5 space-y-4">
        <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
          <div>
            <div className="text-sm text-muted-foreground">{t('review.testRunsForRevision')}</div>
            <div className="font-mono font-semibold text-foreground">{selectedVersion ? `v${selectedVersion.version}` : '—'}</div>
          </div>
          <div className="text-sm text-muted-foreground">{t('review.testRunsCount', { count: testRuns?.length ?? 0 })}</div>
        </div>

        {isLoading ? (
          <div className="space-y-3">
            <div className="h-16 animate-shimmer rounded-xl" />
            <div className="h-16 animate-shimmer rounded-xl" />
          </div>
        ) : testRuns && testRuns.length > 0 ? (
          <div className="space-y-3">
            {testRuns.map((testRun) => (
              <div key={testRun.id} className="rounded-xl border border-border/60 bg-secondary/20 p-4 space-y-3">
                <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                  <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-semibold text-foreground">{testRun.name}</span>
                      <span className={cn('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium', statusBadgeClassName(testRun.status))}>
                        {resolveStatusLabel(testRun.status, t)}
                      </span>
                      <span className="inline-flex items-center rounded-full border border-border px-2.5 py-0.5 text-xs font-medium text-muted-foreground">
                        {resolveSourceLabel(testRun.source, t)}
                      </span>
                    </div>
                    {testRun.summary ? <p className="text-sm text-muted-foreground">{testRun.summary}</p> : null}
                  </div>
                  <div className="text-sm text-muted-foreground">{formatLocalDateTime(testRun.createdAt, i18n.language)}</div>
                </div>

                <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
                  <span>{t('review.testRunCreatedBy', { user: testRun.createdBy })}</span>
                  {testRun.externalUrl ? (
                    <a
                      href={testRun.externalUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="text-primary underline-offset-4 hover:underline"
                    >
                      {t('review.testRunOpenLink')}
                    </a>
                  ) : null}
                </div>

                {testRun.detailsMarkdown ? (
                  <pre className="overflow-x-auto rounded-lg border border-border/60 bg-background/80 p-3 text-xs leading-relaxed text-foreground whitespace-pre-wrap">
                    {testRun.detailsMarkdown}
                  </pre>
                ) : null}
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">{t('review.noTestRuns')}</p>
        )}
      </div>

      {canCreateForSelectedVersion ? (
        <Dialog
          open={dialogOpen}
          onOpenChange={(open) => {
            setDialogOpen(open)
            if (!open) {
              setForm(INITIAL_FORM_STATE)
            }
          }}
        >
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{t('review.addTestRun')}</DialogTitle>
              <DialogDescription>
                {selectedVersion
                  ? t('review.addTestRunDescription', { version: selectedVersion.version })
                  : t('review.testRunsDescription')}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="review-test-run-name">{t('review.testRunNameLabel')}</Label>
                <Input
                  id="review-test-run-name"
                  value={form.name}
                  placeholder={t('review.testRunNamePlaceholder')}
                  onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                />
              </div>

              <div className="grid gap-4 md:grid-cols-2">
                <div className="space-y-2">
                  <Label>{t('review.testRunSourceLabel')}</Label>
                  <Select value={form.source} onValueChange={(value) => setForm((current) => ({ ...current, source: value as ReviewTestRunSource }))}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {SOURCE_OPTIONS.map((source) => (
                        <SelectItem key={source} value={source}>
                          {resolveSourceLabel(source, t)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label>{t('review.testRunStatusLabel')}</Label>
                  <Select value={form.status} onValueChange={(value) => setForm((current) => ({ ...current, status: value as ReviewTestRunStatus }))}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {STATUS_OPTIONS.map((status) => (
                        <SelectItem key={status} value={status}>
                          {resolveStatusLabel(status, t)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="review-test-run-summary">{t('review.testRunSummaryLabel')}</Label>
                <Textarea
                  id="review-test-run-summary"
                  value={form.summary}
                  rows={3}
                  placeholder={t('review.testRunSummaryPlaceholder')}
                  onChange={(event) => setForm((current) => ({ ...current, summary: event.target.value }))}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="review-test-run-details">{t('review.testRunDetailsLabel')}</Label>
                <Textarea
                  id="review-test-run-details"
                  value={form.detailsMarkdown}
                  rows={6}
                  placeholder={t('review.testRunDetailsPlaceholder')}
                  onChange={(event) => setForm((current) => ({ ...current, detailsMarkdown: event.target.value }))}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="review-test-run-link">{t('review.testRunExternalUrlLabel')}</Label>
                <Input
                  id="review-test-run-link"
                  type="url"
                  value={form.externalUrl}
                  placeholder={t('review.testRunExternalUrlPlaceholder')}
                  onChange={(event) => setForm((current) => ({ ...current, externalUrl: event.target.value }))}
                />
              </div>
            </div>

            <DialogFooter>
              <Button variant="outline" onClick={() => setDialogOpen(false)}>
                {t('dialog.close')}
              </Button>
              <Button onClick={handleCreate} disabled={createTestRunMutation.isPending}>
                {t('review.createTestRun')}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      ) : null}
    </Card>
  )
}

function resolveSourceLabel(source: string, t: (key: string) => string) {
  if (source === 'MANUAL') return t('review.testRunSourceManual')
  if (source === 'CI') return t('review.testRunSourceCi')
  if (source === 'SCANNER') return t('review.testRunSourceScanner')
  if (source === 'CUSTOM') return t('review.testRunSourceCustom')
  return source
}

function resolveStatusLabel(status: string, t: (key: string) => string) {
  if (status === 'PASSED') return t('review.testRunStatusPassed')
  if (status === 'FAILED') return t('review.testRunStatusFailed')
  if (status === 'WARNING') return t('review.testRunStatusWarning')
  if (status === 'INFO') return t('review.testRunStatusInfo')
  return status
}

function statusBadgeClassName(status: string) {
  if (status === 'PASSED') return 'bg-emerald-500/10 text-emerald-400'
  if (status === 'FAILED') return 'bg-red-500/10 text-red-400'
  if (status === 'WARNING') return 'bg-amber-500/10 text-amber-400'
  if (status === 'INFO') return 'bg-sky-500/10 text-sky-400'
  return 'bg-secondary text-secondary-foreground'
}

function normalizeOptionalText(value?: string) {
  if (value == null) {
    return undefined
  }
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : undefined
}
