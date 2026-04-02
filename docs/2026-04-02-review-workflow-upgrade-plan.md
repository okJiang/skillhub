# Review Workflow Upgrade Plan

Date: 2026-04-02
Status: proposal based on current code

## 1. Current gap

Current implementation is centered on a single `review_task` record:

- One pending submission maps to one `review_task`
- Final outcome is a single `APPROVED` or `REJECTED`
- Final reviewer feedback is stored in one `review_comment`
- Review permission for team namespaces is limited to namespace `OWNER` and `ADMIN`
- Review detail page can inspect files and active review version, but does not support line comments
- Version comparison already exists on the skill detail page for normal version history, but not as a first-class review workflow primitive
- Security scan output already exists as a separate section, but arbitrary test results are not modeled as review artifacts

This model is too narrow for the requested workflow:

1. all namespace members can review submitted skills
2. multiple submissions / versions in one review stage need visible diffs
3. comments should be attachable to file lines like GitHub review
4. each version stage should carry one or more test results visible to reviewers
5. private skill uploads should not require review

## 2. Existing code constraints

### 2.1 Permission boundary today

`ReviewPermissionChecker.canReviewNamespace(...)` currently allows review only when:

- user has platform role `SKILL_ADMIN` or `SUPER_ADMIN`, or
- user is namespace `OWNER` / `ADMIN`

Namespace `MEMBER` is excluded from review permissions.

### 2.2 Review data model today

`ReviewTask` currently stores:

- `skill_version_id`
- `namespace_id`
- `status`
- `submitted_by`
- `reviewed_by`
- `review_comment`
- timestamps

This supports only a single reviewer decision and a single final summary comment.

### 2.3 Submission/version behavior today

Current publish flow auto-withdraws older pending versions and creates a new pending version. That is good for resubmission semantics, but the old review conversation is not preserved as a first-class review series.

### 2.4 Diff support today

The web skill detail page already has version comparison logic. That reduces implementation risk for requirement 2, because the diff computation and file-list comparison are not greenfield.

### 2.5 Test output today

The platform already shows security scan results, but there is no generic review-time test artifact model. Arbitrary test runs, manual validation records, and repeated execution history are not yet represented.

## 3. Recommended target model

Do not keep stretching `review_task` into a multi-purpose record. Split the review workflow into a review container plus version-stage artifacts.

### 3.1 New concepts

- `review_request`
  - one logical review thread for one skill
  - status: `OPEN`, `APPROVED`, `CHANGES_REQUESTED`, `CLOSED`, `WITHDRAWN`
  - namespace-scoped permission root
- `review_revision`
  - one submitted version inside a review request
  - points to `skill_version_id`
  - sequence number inside the request
  - status: `ACTIVE`, `SUPERSEDED`, `WITHDRAWN`
- `review_comment_thread`
  - top-level review thread
  - can be general or file-line anchored
- `review_comment`
  - one comment item in a thread
  - supports reply chains and resolution
- `review_decision`
  - reviewer vote / decision record
  - `COMMENT`, `APPROVE`, `REQUEST_CHANGES`
- `review_test_run`
  - one attached test result set for a revision
  - supports repeated runs

This is much closer to GitHub’s mental model and maps cleanly to your requested behavior.

## 4. Requirement-by-requirement design

### 4.1 All namespace members can review

Recommended rule for team namespaces:

- `OWNER`, `ADMIN`, `MEMBER` can review
- submitter cannot be the sole approver of their own revision
- final merge/publish gate should require at least one approval from a non-author reviewer

Recommended rule for global namespace:

- keep platform roles `SKILL_ADMIN` / `SUPER_ADMIN`

Implementation note:

- change `ReviewPermissionChecker.canReviewNamespace(...)` for team namespaces to include `MEMBER`
- keep a separate finalization rule so “can comment/review” is broader than “can complete publish” if you want stronger governance later

### 4.2 Diff across multiple submitted versions

Each resubmission should create a new `review_revision` instead of replacing history.

UI behavior:

- review page shows a revision timeline: `v1`, `v2`, `v3`
- default diff compares current revision vs previous revision
- optional compare base can be:
  - previous revision
  - latest published version
  - arbitrary earlier revision in the same review request

Implementation note:

- reuse existing file-manifest and metadata diff logic from the skill detail page
- move that diff logic into a shared review/version diff service instead of keeping it page-local

### 4.3 Line comments like GitHub review

Add file-anchored comment threads with these fields:

- `review_revision_id`
- `file_path`
- `side` (`BASE` / `HEAD`)
- `line_number`
- `line_anchor`
- `original_line_number` for outdated-thread tracking
- `status` (`OPEN`, `RESOLVED`, `OUTDATED`)

UI behavior:

- split diff or unified diff view
- line gutter action to add comment
- thread badges for open/resolved/outdated
- outdated comment threads stay visible when later revisions shift lines

Do not try to anchor comments only by raw line number. Store a stable hunk/line anchor so later revisions can mark comments as outdated instead of silently misplacing them.

### 4.4 Test results per version stage

Create a generic `review_test_run` model:

- `review_revision_id`
- `source` (`MANUAL`, `CI`, `SCANNER`, `CUSTOM`)
- `name`
- `status` (`PASSED`, `FAILED`, `WARNING`, `INFO`)
- `summary`
- `details_markdown` or structured JSON payload
- `external_url`
- `created_by`
- timestamps

Behavior:

- one revision can have multiple test runs
- later runs do not overwrite earlier runs
- review UI shows reverse chronological runs
- reviewers can filter by latest/all

This should absorb current security scan output over time, even if phase 1 keeps scanner UI separate.

### 4.5 Private skill uploads skip review

Recommended policy:

- when requested visibility is `PRIVATE`, publish directly without review
- scope visibility to owner plus namespace `ADMIN` / `OWNER` exactly as current visibility rules already do
- still create audit logs
- optionally create a lightweight “self-published private revision” activity record, but not a review request

This is a low-risk change because private skills are not externally visible and already have tighter read access.

Implementation note:

- current `SkillPublishService` only auto-publishes for `SUPER_ADMIN`
- extend auto-publish rule to:
  - `forceAutoPublish`
  - `SUPER_ADMIN`
  - or `visibility == PRIVATE`

## 5. API changes

Recommended new endpoints:

- `GET /api/web/reviews/{id}/revisions`
- `GET /api/web/reviews/{id}/revisions/{revisionId}/diff`
- `GET /api/web/reviews/{id}/comments`
- `POST /api/web/reviews/{id}/comments`
- `POST /api/web/reviews/comment-threads/{threadId}/resolve`
- `POST /api/web/reviews/{id}/decisions`
- `GET /api/web/reviews/{id}/test-runs`
- `POST /api/web/reviews/{id}/revisions/{revisionId}/test-runs`

Recommended compatibility strategy:

- keep existing approve/reject endpoints temporarily
- map them internally to decision records plus final review-request state transition
- remove direct dependence on `review_task.review_comment` from web UI

## 6. UI changes

Review detail page should be reorganized into four panels:

- Revision timeline
- Diff viewer
- Conversations
- Test results

Suggested interaction:

- selecting a revision updates diff and comments context
- line comments are added from the diff gutter
- right sidebar shows open threads and test summary
- final actions are `Approve`, `Request changes`, `Comment only`

The existing review skill detail page is a good base for file browsing, but it is not enough for review UX by itself.

## 7. Delivery phases

### Phase 1: policy + private-skip + revision history

- allow namespace `MEMBER` to review team namespace submissions
- skip review for `PRIVATE` visibility uploads
- preserve re-submission history as revision timeline
- add review detail diff between revisions

This phase delivers business value quickly with limited UI disruption.

### Phase 2: structured review conversations

- add general comments and file-line comment threads
- add reviewer decisions (`APPROVE`, `REQUEST_CHANGES`, `COMMENT`)
- update final status computation from single review record to aggregated decisions

This is the largest behavioral change.

### Phase 3: test results and workflow polish

- add revision-bound test results
- surface latest result summary in review list/detail
- optionally unify security scan output into the same artifact system

## 8. Migration advice

Do not mutate the meaning of `review_task` in place too aggressively.

Safer migration path:

1. introduce new tables next to `review_task`
2. backfill one `review_request` and one `review_revision` from each existing task
3. keep old endpoints writing both old and new models during transition
4. switch reads to new model
5. remove old fields only after web UI and API clients are fully migrated

## 9. Key decisions still needed

Before implementation, product/ops should confirm:

1. Does final approval require one reviewer or multiple reviewers?
2. Can the author comment on their own review diff? I recommend yes.
3. Can the author approve their own change? I recommend no for final publish.
4. Are test results manual upload only, or should CI push them by API?
5. Should namespace members also see all historical review threads, or only active ones?

## 10. Recommendation

If the goal is internal deployment with minimal delay, implement in this order:

1. private upload skip review
2. namespace member review permission
3. revision history + diff in review page
4. line comments
5. test result artifacts

That sequence minimizes schema risk early and gets your most important workflow changes online first.
