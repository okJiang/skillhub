import { renderToStaticMarkup } from 'react-dom/server'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, values?: Record<string, string | number>) => {
        if (values?.line) return `${key}:${values.line}`
        if (values?.count != null) return `${key}:${values.count}`
        return key
      },
      i18n: { language: 'zh' },
    }),
  }
})

vi.mock('@/shared/lib/date-time', () => ({
  formatLocalDateTime: (value: string) => value,
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

vi.mock('@/shared/ui/dialog', () => ({
  Dialog: ({ children }: { children: ReactNode }) => <>{children}</>,
  DialogContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/features/review/review-error', () => ({
  resolveReviewActionErrorDescription: () => 'error',
}))

vi.mock('./use-review-detail', () => ({
  useReviewCommentThreads: () => ({
    data: [
      {
        id: 9,
        reviewTaskId: 13,
        skillVersionId: 10,
        filePath: 'README.md',
        lineNumber: 2,
        createdBy: 'admin',
        createdAt: '2026-04-02T10:00:00Z',
        comments: [
          {
            id: 10,
            threadId: 9,
            body: 'Please clarify this step.',
            createdBy: 'admin',
            createdAt: '2026-04-02T10:01:00Z',
          },
          {
            id: 11,
            threadId: 9,
            body: 'Done in the follow-up revision.',
            createdBy: 'owner',
            createdAt: '2026-04-02T10:02:00Z',
          },
        ],
      },
    ],
    isLoading: false,
  }),
  useCreateReviewCommentThread: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
  useCreateReviewComment: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}))

import { ReviewFileCommentsDialog } from './review-file-comments-dialog'

describe('ReviewFileCommentsDialog', () => {
  it('renders threaded line comments beside the previewed file', () => {
    const html = renderToStaticMarkup(
      <ReviewFileCommentsDialog
        open
        onOpenChange={vi.fn()}
        node={{
          id: 'README.md',
          name: 'README.md',
          path: 'README.md',
          type: 'file',
          depth: 0,
          file: {
            id: 1,
            filePath: 'README.md',
            fileSize: 120,
            contentType: 'text/markdown',
            sha256: 'sha',
          },
        }}
        content={'# Demo\nInstall the package\nDone'}
        isLoading={false}
        error={null}
        onDownload={vi.fn()}
        taskId={13}
        versionId={10}
        canComment
      />
    )

    expect(html).toContain('Please clarify this step.')
    expect(html).toContain('Done in the follow-up revision.')
    expect(html).toContain('review.lineCommentAnchor:2')
    expect(html).toContain('review.inlineCommentCount:1')
  })
})
