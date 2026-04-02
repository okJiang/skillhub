package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewComment;
import com.iflytek.skillhub.domain.review.ReviewCommentRepository;
import com.iflytek.skillhub.domain.review.ReviewCommentThread;
import com.iflytek.skillhub.domain.review.ReviewCommentThreadRepository;
import com.iflytek.skillhub.domain.review.ReviewTestRun;
import com.iflytek.skillhub.domain.review.ReviewTestRunRepository;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.CreateReviewCommentRequest;
import com.iflytek.skillhub.dto.CreateReviewCommentThreadRequest;
import com.iflytek.skillhub.dto.CreateReviewTestRunRequest;
import com.iflytek.skillhub.dto.ReviewCommentResponse;
import com.iflytek.skillhub.dto.ReviewCommentThreadResponse;
import com.iflytek.skillhub.dto.ReviewVersionSnapshotResponse;
import com.iflytek.skillhub.dto.ReviewSkillDetailResponse;
import com.iflytek.skillhub.dto.ReviewTestRunResponse;
import com.iflytek.skillhub.dto.SkillDetailResponse;
import com.iflytek.skillhub.dto.SkillFileResponse;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillVersionResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReviewSkillDetailAppService {

    private static final long MAX_PREVIEW_SIZE_BYTES = 1024L * 1024L;
    private static final Set<String> PREVIEWABLE_EXTENSIONS = Set.of(
            "md", "mdx", "markdown",
            "ts", "tsx", "js", "jsx", "json", "yaml", "yml",
            "py", "java", "go", "rs", "c", "cpp", "h", "hpp",
            "sh", "bash", "zsh", "fish",
            "txt", "xml", "xsd", "xsl", "dtd", "toml", "ini", "env",
            "html", "css", "scss", "sass", "less",
            "vue", "svelte"
    );
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "ico", "svg",
            "mp4", "avi", "mov", "wmv", "flv", "webm",
            "mp3", "wav", "ogg", "flac",
            "zip", "tar", "gz", "rar", "7z",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "exe", "dll", "so", "dylib"
    );

    private final ReviewTaskRepository reviewTaskRepository;
    private final NamespaceRepository namespaceRepository;
    private final ReviewService reviewService;
    private final RbacService rbacService;
    private final SkillQueryService skillQueryService;
    private final SkillDownloadService skillDownloadService;
    private final ReviewTestRunRepository reviewTestRunRepository;
    private final ReviewCommentThreadRepository reviewCommentThreadRepository;
    private final ReviewCommentRepository reviewCommentRepository;

    public ReviewSkillDetailAppService(ReviewTaskRepository reviewTaskRepository,
                                       NamespaceRepository namespaceRepository,
                                       ReviewService reviewService,
                                       RbacService rbacService,
                                       SkillQueryService skillQueryService,
                                       SkillDownloadService skillDownloadService,
                                       ReviewTestRunRepository reviewTestRunRepository,
                                       ReviewCommentThreadRepository reviewCommentThreadRepository,
                                       ReviewCommentRepository reviewCommentRepository) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.namespaceRepository = namespaceRepository;
        this.reviewService = reviewService;
        this.rbacService = rbacService;
        this.skillQueryService = skillQueryService;
        this.skillDownloadService = skillDownloadService;
        this.reviewTestRunRepository = reviewTestRunRepository;
        this.reviewCommentThreadRepository = reviewCommentThreadRepository;
        this.reviewCommentRepository = reviewCommentRepository;
    }

    public ReviewSkillDetailResponse getReviewSkillDetail(Long reviewId,
                                                          String userId,
                                                          Map<Long, NamespaceRole> userNsRoles) {
        AuthorizedReviewSnapshot authorizedSnapshot = loadAuthorizedReviewSnapshot(reviewId, userId, userNsRoles);
        ReviewAccessContext context = authorizedSnapshot.accessContext();
        SkillQueryService.ReviewSkillSnapshotDTO snapshot = authorizedSnapshot.reviewSnapshot();

        SkillDetailResponse skill = new SkillDetailResponse(
                snapshot.skill().getId(),
                snapshot.skill().getSlug(),
                snapshot.skill().getDisplayName(),
                snapshot.skill().getOwnerId(),
                snapshot.ownerDisplayName(),
                snapshot.skill().getSummary(),
                snapshot.skill().getVisibility().name(),
                snapshot.skill().getStatus().name(),
                snapshot.skill().getDownloadCount(),
                snapshot.skill().getStarCount(),
                snapshot.skill().getRatingAvg(),
                snapshot.skill().getRatingCount(),
                snapshot.skill().isHidden(),
                context.namespace().getSlug(),
                List.of(),
                false,
                false,
                false,
                false,
                toLifecycleVersion(snapshot.activeVersion()),
                snapshot.publishedVersion() != null ? toLifecycleVersion(snapshot.publishedVersion()) : null,
                toLifecycleVersion(snapshot.activeVersion()),
                null,
                "REVIEW_TASK"
        );

        List<SkillVersionResponse> versions = snapshot.versions().stream()
                .map(version -> new SkillVersionResponse(
                        version.getId(),
                        version.getVersion(),
                        version.getStatus().name(),
                        version.getChangelog(),
                        version.getFileCount(),
                        version.getTotalSize(),
                        version.getPublishedAt(),
                        version.getId().equals(snapshot.activeVersion().getId())
                                || skillQueryService.isDownloadAvailable(version)
                ))
                .toList();

        List<SkillFileResponse> files = snapshot.files().stream()
                .map(this::toFileResponse)
                .toList();

        return new ReviewSkillDetailResponse(
                skill,
                versions,
                files,
                snapshot.documentationPath(),
                snapshot.documentationContent(),
                "/api/v1/reviews/" + reviewId + "/download",
                snapshot.activeVersion().getVersion()
        );
    }

    public SkillDownloadService.DownloadResult downloadReviewPackage(Long reviewId,
                                                                    String userId,
                                                                    Map<Long, NamespaceRole> userNsRoles) {
        AuthorizedReviewSnapshot authorizedSnapshot = loadAuthorizedReviewSnapshot(reviewId, userId, userNsRoles);
        SkillQueryService.ReviewSkillSnapshotDTO snapshot = authorizedSnapshot.reviewSnapshot();
        return skillDownloadService.downloadReviewVersion(snapshot.skill(), snapshot.activeVersion());
    }

    public ReviewVersionSnapshotResponse getReviewVersionSnapshot(Long reviewId,
                                                                  Long versionId,
                                                                  String userId,
                                                                  Map<Long, NamespaceRole> userNsRoles) {
        AuthorizedReviewSnapshot authorizedSnapshot = loadAuthorizedReviewSnapshot(reviewId, userId, userNsRoles);
        SkillQueryService.ReviewSkillSnapshotDTO reviewSnapshot = authorizedSnapshot.reviewSnapshot();
        requireReviewScopedVersion(reviewSnapshot, versionId);

        SkillQueryService.ReviewVersionSnapshotDTO versionSnapshot =
                skillQueryService.getReviewVersionSnapshot(versionId);

        if (!reviewSnapshot.skill().getId().equals(versionSnapshot.skillId())) {
            throw new DomainForbiddenException("review.no_permission");
        }

        return new ReviewVersionSnapshotResponse(
                versionSnapshot.version().getId(),
                versionSnapshot.version().getVersion(),
                versionSnapshot.version().getStatus().name(),
                versionSnapshot.version().getParsedMetadataJson(),
                versionSnapshot.files().stream().map(this::toFileResponse).toList(),
                versionSnapshot.documentationPath(),
                versionSnapshot.documentationContent()
        );
    }

    public List<ReviewTestRunResponse> listReviewTestRuns(Long reviewId,
                                                          Long versionId,
                                                          String userId,
                                                          Map<Long, NamespaceRole> userNsRoles) {
        AuthorizedReviewSnapshot authorizedSnapshot = loadAuthorizedReviewSnapshot(reviewId, userId, userNsRoles);
        SkillVersion scopedVersion = requireReviewScopedVersion(authorizedSnapshot.reviewSnapshot(), versionId);
        Long reviewTaskId = resolveReviewTaskIdForVersion(authorizedSnapshot.accessContext(), scopedVersion);
        if (reviewTaskId == null) {
            return List.of();
        }
        return reviewTestRunRepository.findByReviewTaskIdAndSkillVersionIdOrderByCreatedAtDesc(
                        reviewTaskId,
                        scopedVersion.getId()
                ).stream()
                .map(this::toReviewTestRunResponse)
                .toList();
    }

    public ReviewTestRunResponse createReviewTestRun(Long reviewId,
                                                     Long versionId,
                                                     CreateReviewTestRunRequest request,
                                                     String userId,
                                                     Map<Long, NamespaceRole> userNsRoles) {
        AuthorizedReviewSnapshot authorizedSnapshot = loadAuthorizedReviewSnapshot(reviewId, userId, userNsRoles);
        ensureTaskPending(authorizedSnapshot.accessContext().task());
        requireActiveReviewVersion(authorizedSnapshot.reviewSnapshot(), versionId);
        ReviewTestRun saved = reviewTestRunRepository.save(new ReviewTestRun(
                authorizedSnapshot.accessContext().task().getId(),
                versionId,
                request.source(),
                request.status(),
                request.name(),
                request.summary(),
                request.detailsMarkdown(),
                request.externalUrl(),
                userId
        ));
        return toReviewTestRunResponse(saved);
    }

    public List<ReviewCommentThreadResponse> listReviewCommentThreads(Long reviewId,
                                                                      Long versionId,
                                                                      String filePath,
                                                                      String userId,
                                                                      Map<Long, NamespaceRole> userNsRoles) {
        AuthorizedReviewSnapshot authorizedSnapshot = loadAuthorizedReviewSnapshot(reviewId, userId, userNsRoles);
        requireActiveReviewVersion(authorizedSnapshot.reviewSnapshot(), versionId);
        assertCommentableFile(versionId, filePath);
        List<ReviewCommentThread> threads =
                reviewCommentThreadRepository.findByReviewTaskIdAndSkillVersionIdAndFilePathOrderByLineNumberAscCreatedAtAsc(
                        reviewId,
                        versionId,
                        normalizeFilePath(filePath)
                );
        return toReviewCommentThreadResponses(threads);
    }

    public ReviewCommentThreadResponse createReviewCommentThread(Long reviewId,
                                                                 Long versionId,
                                                                 CreateReviewCommentThreadRequest request,
                                                                 String userId,
                                                                 Map<Long, NamespaceRole> userNsRoles) {
        AuthorizedReviewSnapshot authorizedSnapshot = loadAuthorizedReviewSnapshot(reviewId, userId, userNsRoles);
        ensureTaskPending(authorizedSnapshot.accessContext().task());
        requireActiveReviewVersion(authorizedSnapshot.reviewSnapshot(), versionId);
        assertCommentableLine(versionId, request.filePath(), request.lineNumber());

        ReviewCommentThread savedThread = reviewCommentThreadRepository.save(new ReviewCommentThread(
                reviewId,
                versionId,
                request.filePath(),
                request.lineNumber(),
                userId
        ));
        ReviewComment savedComment = reviewCommentRepository.save(new ReviewComment(
                savedThread.getId(),
                request.body(),
                userId
        ));
        ReviewCommentResponse initialComment = toReviewCommentResponse(savedComment);
        return toReviewCommentThreadResponse(savedThread, List.<ReviewCommentResponse>of(initialComment));
    }

    public ReviewCommentResponse createReviewComment(Long reviewId,
                                                     Long threadId,
                                                     CreateReviewCommentRequest request,
                                                     String userId,
                                                     Map<Long, NamespaceRole> userNsRoles) {
        AuthorizedReviewSnapshot authorizedSnapshot = loadAuthorizedReviewSnapshot(reviewId, userId, userNsRoles);
        ensureTaskPending(authorizedSnapshot.accessContext().task());

        ReviewCommentThread thread = reviewCommentThreadRepository.findById(threadId)
                .orElseThrow(() -> new DomainNotFoundException("review.comment_thread.not_found", threadId));
        if (!reviewId.equals(thread.getReviewTaskId())) {
            throw new DomainForbiddenException("review.no_permission");
        }
        requireActiveReviewVersion(authorizedSnapshot.reviewSnapshot(), thread.getSkillVersionId());
        assertCommentableLine(thread.getSkillVersionId(), thread.getFilePath(), thread.getLineNumber());

        ReviewComment savedComment = reviewCommentRepository.save(new ReviewComment(
                threadId,
                request.body(),
                userId
        ));
        return toReviewCommentResponse(savedComment);
    }

    /**
     * Reads a single file's content from the review's bound skill version.
     * Reuses the existing review authorization context to ensure only
     * authorized reviewers can access the file.
     */
    public InputStream getReviewFileContent(Long reviewId,
                                            String filePath,
                                            String userId,
                                            Map<Long, NamespaceRole> userNsRoles) {
        ReviewAccessContext context = loadAuthorizedContext(reviewId, userId, userNsRoles);
        return skillQueryService.getFileContentByVersionId(
                context.task().getSkillVersionId(),
                filePath
        );
    }

    private ReviewAccessContext loadAuthorizedContext(Long reviewId,
                                                      String userId,
                                                      Map<Long, NamespaceRole> userNsRoles) {
        ReviewTask task = reviewTaskRepository.findById(reviewId)
                .orElseThrow(() -> new DomainNotFoundException("review_task.not_found", reviewId));
        Namespace namespace = namespaceRepository.findById(task.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", task.getNamespaceId()));
        Map<Long, NamespaceRole> namespaceRoles = userNsRoles != null ? userNsRoles : Map.of();
        Set<String> platformRoles = rbacService.getUserRoleCodes(userId);
        if (!reviewService.canViewReview(task, userId, namespace.getType(), namespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("review.no_permission");
        }
        return new ReviewAccessContext(task, namespace);
    }

    private SkillFileResponse toFileResponse(SkillFile file) {
        return new SkillFileResponse(
                file.getId(),
                file.getFilePath(),
                file.getFileSize(),
                file.getContentType(),
                file.getSha256()
        );
    }

    private AuthorizedReviewSnapshot loadAuthorizedReviewSnapshot(Long reviewId,
                                                                  String userId,
                                                                  Map<Long, NamespaceRole> userNsRoles) {
        ReviewAccessContext context = loadAuthorizedContext(reviewId, userId, userNsRoles);
        SkillQueryService.ReviewSkillSnapshotDTO reviewSnapshot =
                skillQueryService.getReviewSkillSnapshot(context.task().getSkillVersionId());
        return new AuthorizedReviewSnapshot(context, reviewSnapshot);
    }

    private SkillVersion requireReviewScopedVersion(SkillQueryService.ReviewSkillSnapshotDTO reviewSnapshot,
                                                    Long versionId) {
        return reviewSnapshot.versions().stream()
                .filter(version -> versionId.equals(version.getId()))
                .findFirst()
                .orElseThrow(() -> new DomainForbiddenException("review.no_permission"));
    }

    private Long resolveReviewTaskIdForVersion(ReviewAccessContext accessContext, SkillVersion version) {
        if (version.getId().equals(accessContext.task().getSkillVersionId())) {
            return accessContext.task().getId();
        }
        if (version.getStatus() == SkillVersionStatus.PUBLISHED) {
            return null;
        }
        return reviewTaskRepository.findFirstBySkillVersionIdOrderByIdDesc(version.getId())
                .map(ReviewTask::getId)
                .orElse(null);
    }

    private void ensureTaskPending(ReviewTask task) {
        if (task.getStatus() != ReviewTaskStatus.PENDING) {
            throw new DomainBadRequestException("review.not_pending", task.getId());
        }
    }

    private SkillVersion requireActiveReviewVersion(SkillQueryService.ReviewSkillSnapshotDTO reviewSnapshot,
                                                    Long versionId) {
        SkillVersion activeVersion = reviewSnapshot.activeVersion();
        if (activeVersion == null || !versionId.equals(activeVersion.getId())) {
            throw new DomainForbiddenException("review.no_permission");
        }
        return activeVersion;
    }

    private void assertCommentableFile(Long versionId, String filePath) {
        String normalizedFilePath = normalizeFilePath(filePath);
        SkillFile targetFile = skillQueryService.getReviewVersionSnapshot(versionId).files().stream()
                .filter(file -> normalizedFilePath.equals(file.getFilePath()))
                .findFirst()
                .orElseThrow(() -> new DomainBadRequestException("review.comment.file_not_found", normalizedFilePath));
        if (!isPreviewableFile(targetFile)) {
            throw new DomainBadRequestException("review.comment.file_not_previewable", normalizedFilePath);
        }
    }

    private void assertCommentableLine(Long versionId, String filePath, Integer lineNumber) {
        String normalizedFilePath = normalizeFilePath(filePath);
        assertCommentableFile(versionId, normalizedFilePath);
        int lineCount = countFileLines(versionId, normalizedFilePath);
        if (lineNumber == null || lineNumber > lineCount) {
            throw new DomainBadRequestException("review.comment.line_number_out_of_range", lineNumber, lineCount);
        }
    }

    private boolean isPreviewableFile(SkillFile file) {
        if (file.getFileSize() > MAX_PREVIEW_SIZE_BYTES) {
            return false;
        }
        String extension = extractFileExtension(file.getFilePath());
        if (BINARY_EXTENSIONS.contains(extension)) {
            return false;
        }
        return extension.isEmpty() || PREVIEWABLE_EXTENSIONS.contains(extension);
    }

    private String extractFileExtension(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filePath.length() - 1) {
            return "";
        }
        return filePath.substring(dotIndex + 1).toLowerCase();
    }

    private int countFileLines(Long versionId, String filePath) {
        try (InputStream content = skillQueryService.getFileContentByVersionId(versionId, filePath)) {
            String normalizedContent = new String(content.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            return normalizedContent.split("\n", -1).length;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read review file content for line validation", ex);
        }
    }

    private String normalizeFilePath(String filePath) {
        if (filePath == null) {
            return "";
        }
        String normalized = filePath.trim();
        if (normalized.isEmpty()) {
            throw new DomainBadRequestException("review.comment.file_not_found", normalized);
        }
        return normalized;
    }

    private ReviewTestRunResponse toReviewTestRunResponse(ReviewTestRun reviewTestRun) {
        return new ReviewTestRunResponse(
                reviewTestRun.getId(),
                reviewTestRun.getSkillVersionId(),
                reviewTestRun.getSource().name(),
                reviewTestRun.getStatus().name(),
                reviewTestRun.getName(),
                reviewTestRun.getSummary(),
                reviewTestRun.getDetailsMarkdown(),
                reviewTestRun.getExternalUrl(),
                reviewTestRun.getCreatedBy(),
                reviewTestRun.getCreatedAt()
        );
    }

    private List<ReviewCommentThreadResponse> toReviewCommentThreadResponses(List<ReviewCommentThread> threads) {
        if (threads.isEmpty()) {
            return List.of();
        }

        Map<Long, List<ReviewCommentResponse>> commentsByThreadId =
                reviewCommentRepository.findByThreadIdInOrderByCreatedAtAsc(
                                threads.stream().map(ReviewCommentThread::getId).toList()
                        ).stream()
                        .map(this::toReviewCommentResponse)
                        .collect(Collectors.groupingBy(
                                ReviewCommentResponse::threadId,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        return threads.stream()
                .map(thread -> toReviewCommentThreadResponse(
                        thread,
                        commentsByThreadId.getOrDefault(thread.getId(), List.<ReviewCommentResponse>of())
                ))
                .toList();
    }

    private ReviewCommentThreadResponse toReviewCommentThreadResponse(ReviewCommentThread thread,
                                                                     List<ReviewCommentResponse> comments) {
        return new ReviewCommentThreadResponse(
                thread.getId(),
                thread.getReviewTaskId(),
                thread.getSkillVersionId(),
                thread.getFilePath(),
                thread.getLineNumber(),
                thread.getCreatedBy(),
                thread.getCreatedAt(),
                comments
        );
    }

    private ReviewCommentResponse toReviewCommentResponse(ReviewComment reviewComment) {
        return new ReviewCommentResponse(
                reviewComment.getId(),
                reviewComment.getThreadId(),
                reviewComment.getBody(),
                reviewComment.getCreatedBy(),
                reviewComment.getCreatedAt()
        );
    }

    private SkillLifecycleVersionResponse toLifecycleVersion(SkillVersion version) {
        return new SkillLifecycleVersionResponse(version.getId(), version.getVersion(), version.getStatus().name());
    }

    private record ReviewAccessContext(ReviewTask task, Namespace namespace) {}

    private record AuthorizedReviewSnapshot(ReviewAccessContext accessContext,
                                            SkillQueryService.ReviewSkillSnapshotDTO reviewSnapshot) {}
}
