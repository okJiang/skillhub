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
import com.iflytek.skillhub.domain.review.ReviewTestRunSource;
import com.iflytek.skillhub.domain.review.ReviewTestRunStatus;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.CreateReviewCommentRequest;
import com.iflytek.skillhub.dto.CreateReviewCommentThreadRequest;
import com.iflytek.skillhub.dto.ReviewCommentResponse;
import com.iflytek.skillhub.dto.ReviewCommentThreadResponse;
import com.iflytek.skillhub.dto.CreateReviewTestRunRequest;
import com.iflytek.skillhub.dto.ReviewSkillDetailResponse;
import com.iflytek.skillhub.dto.ReviewTestRunResponse;
import com.iflytek.skillhub.dto.ReviewVersionSnapshotResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewSkillDetailAppServiceTest {

    @Mock
    private ReviewTaskRepository reviewTaskRepository;
    @Mock
    private NamespaceRepository namespaceRepository;
    @Mock
    private ReviewService reviewService;
    @Mock
    private RbacService rbacService;
    @Mock
    private SkillQueryService skillQueryService;
    @Mock
    private SkillDownloadService skillDownloadService;
    @Mock
    private ReviewTestRunRepository reviewTestRunRepository;
    @Mock
    private ReviewCommentThreadRepository reviewCommentThreadRepository;
    @Mock
    private ReviewCommentRepository reviewCommentRepository;

    private ReviewSkillDetailAppService service;

    @BeforeEach
    void setUp() {
        service = new ReviewSkillDetailAppService(
                reviewTaskRepository,
                namespaceRepository,
                reviewService,
                rbacService,
                skillQueryService,
                skillDownloadService,
                reviewTestRunRepository,
                reviewCommentThreadRepository,
                reviewCommentRepository
        );
    }

    @Test
    void getReviewSkillDetail_returnsPendingReviewVersionPayload() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion pending = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        SkillVersion published = createVersion(100L, 101L, "1.1.0", SkillVersionStatus.PUBLISHED);
        SkillFile readme = createFile(1L, 101L, "README.md");

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        pending,
                        published,
                        List.of(pending, published),
                        List.of(readme),
                        "README.md",
                        "# demo"
                )
        );

        ReviewSkillDetailResponse response = service.getReviewSkillDetail(42L, "admin", Map.of());

        assertThat(response.activeVersion()).isEqualTo("1.2.0");
        assertThat(response.downloadUrl()).isEqualTo("/api/v1/reviews/42/download");
        assertThat(response.documentationPath()).isEqualTo("README.md");
        assertThat(response.documentationContent()).isEqualTo("# demo");
        assertThat(response.skill().namespace()).isEqualTo("team-a");
        assertThat(response.versions()).extracting("version").containsExactly("1.2.0", "1.1.0");
        assertThat(response.versions().get(0).downloadAvailable()).isTrue();
    }

    @Test
    void getReviewSkillDetail_keepsReviewBoundVersionWhenPublishedVersionAlsoExists() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion pending = createVersion(101L, 101L, "2.0.0-rc1", SkillVersionStatus.PENDING_REVIEW);
        SkillVersion published = createVersion(100L, 101L, "1.9.0", SkillVersionStatus.PUBLISHED);

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        pending,
                        published,
                        List.of(published, pending),
                        List.of(),
                        null,
                        null
                )
        );

        ReviewSkillDetailResponse response = service.getReviewSkillDetail(42L, "admin", Map.of());

        assertThat(response.activeVersion()).isEqualTo("2.0.0-rc1");
        assertThat(response.skill().headlineVersion().version()).isEqualTo("2.0.0-rc1");
        assertThat(response.skill().publishedVersion().version()).isEqualTo("1.9.0");
    }

    @Test
    void getReviewVersionSnapshot_returnsVersionBoundDiffPayload() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        SkillVersion previous = createVersion(99L, 101L, "1.1.0", SkillVersionStatus.REJECTED);
        SkillFile readme = createFile(1L, 99L, "README.md");
        setField(previous, "parsedMetadataJson", "{\"version\":\"1.1.0\"}");

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        null,
                        List.of(active, previous),
                        List.of(),
                        null,
                        null
                )
        );
        given(skillQueryService.getReviewVersionSnapshot(99L)).willReturn(
                new SkillQueryService.ReviewVersionSnapshotDTO(
                        101L,
                        previous,
                        List.of(readme),
                        "README.md",
                        "# old"
                )
        );

        ReviewVersionSnapshotResponse response = service.getReviewVersionSnapshot(42L, 99L, "admin", Map.of());

        assertThat(response.version()).isEqualTo("1.1.0");
        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.documentationPath()).isEqualTo("README.md");
        assertThat(response.documentationContent()).isEqualTo("# old");
        assertThat(response.files()).extracting("filePath").containsExactly("README.md");
    }

    @Test
    void getReviewVersionSnapshot_rejectsVersionsOutsideReviewScopedHistory() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        SkillVersion previous = createVersion(99L, 101L, "1.1.0", SkillVersionStatus.SUPERSEDED);

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        null,
                        List.of(active, previous),
                        List.of(),
                        null,
                        null
                )
        );

        assertThatThrownBy(() -> service.getReviewVersionSnapshot(42L, 88L, "admin", Map.of()))
                .isInstanceOf(DomainForbiddenException.class);
        verify(skillQueryService, never()).getReviewVersionSnapshot(88L);
    }

    @Test
    void downloadReviewPackage_delegatesToReviewDownloadFlow() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion pending = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        SkillDownloadService.DownloadResult result = new SkillDownloadService.DownloadResult(
                () -> new ByteArrayInputStream("zip".getBytes()),
                "skill-a-1.2.0.zip",
                3L,
                "application/zip",
                null,
                true
        );

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        pending,
                        null,
                        List.of(pending),
                        List.of(),
                        null,
                        null
                )
        );
        given(skillDownloadService.downloadReviewVersion(skill, pending)).willReturn(result);

        SkillDownloadService.DownloadResult downloadResult = service.downloadReviewPackage(42L, "admin", Map.of());

        assertThat(downloadResult.filename()).isEqualTo("skill-a-1.2.0.zip");
        assertThat(downloadResult.contentLength()).isEqualTo(3L);
    }

    @Test
    void listReviewTestRuns_returnsRevisionScopedArtifacts() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        ReviewTask supersededTask = createReviewTask(41L, 99L, 20L, "submitter", ReviewTaskStatus.SUPERSEDED);
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        SkillVersion previous = createVersion(99L, 101L, "1.1.0", SkillVersionStatus.SUPERSEDED);
        ReviewTestRun ciRun = createReviewTestRun(7L, 41L, 99L, ReviewTestRunSource.CI, ReviewTestRunStatus.PASSED, "CI smoke");

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        null,
                        List.of(active, previous),
                        List.of(),
                        null,
                        null
                )
        );
        given(reviewTaskRepository.findFirstBySkillVersionIdOrderByIdDesc(99L)).willReturn(Optional.of(supersededTask));
        given(reviewTestRunRepository.findByReviewTaskIdAndSkillVersionIdOrderByCreatedAtDesc(41L, 99L))
                .willReturn(List.of(ciRun));

        List<ReviewTestRunResponse> response = service.listReviewTestRuns(42L, 99L, "admin", Map.of());

        assertThat(response).singleElement().satisfies(run -> {
            assertThat(run.name()).isEqualTo("CI smoke");
            assertThat(run.source()).isEqualTo("CI");
            assertThat(run.status()).isEqualTo("PASSED");
        });
    }

    @Test
    void listReviewTestRuns_returnsEmptyForPublishedBaselineRevision() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        SkillVersion baseline = createVersion(90L, 101L, "1.0.0", SkillVersionStatus.PUBLISHED);

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        baseline,
                        List.of(active, baseline),
                        List.of(),
                        null,
                        null
                )
        );

        List<ReviewTestRunResponse> response = service.listReviewTestRuns(42L, 90L, "admin", Map.of());

        assertThat(response).isEmpty();
        verify(reviewTaskRepository, never()).findFirstBySkillVersionIdOrderByIdDesc(90L);
        verify(reviewTestRunRepository, never())
                .findByReviewTaskIdAndSkillVersionIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void createReviewTestRun_rejectsVersionsOutsideReviewScopedHistory() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        null,
                        List.of(active),
                        List.of(),
                        null,
                        null
                )
        );

        assertThatThrownBy(() -> service.createReviewTestRun(
                42L,
                88L,
                new CreateReviewTestRunRequest(ReviewTestRunSource.MANUAL, ReviewTestRunStatus.FAILED, "Manual smoke", "summary", "details", null),
                "admin",
                Map.of()
        )).isInstanceOf(DomainForbiddenException.class);
        verify(reviewTestRunRepository, never()).save(any());
    }

    @Test
    void createReviewTestRun_savesRunForAllowedRevision() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        ReviewTestRun savedRun = createReviewTestRun(11L, 42L, 101L, ReviewTestRunSource.MANUAL, ReviewTestRunStatus.WARNING, "Manual smoke");

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        null,
                        List.of(active),
                        List.of(),
                        null,
                        null
                )
        );
        given(reviewTestRunRepository.save(any(ReviewTestRun.class))).willReturn(savedRun);

        ReviewTestRunResponse response = service.createReviewTestRun(
                42L,
                101L,
                new CreateReviewTestRunRequest(ReviewTestRunSource.MANUAL, ReviewTestRunStatus.WARNING, "Manual smoke", "summary", "details", "https://ci.example.com/11"),
                "admin",
                Map.of()
        );

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.status()).isEqualTo("WARNING");
        assertThat(response.createdBy()).isEqualTo("admin");
        verify(reviewTestRunRepository).save(argThat(reviewTestRun ->
                reviewTestRun.getReviewTaskId().equals(42L)
                        && reviewTestRun.getSkillVersionId().equals(101L)
        ));
    }

    @Test
    void createReviewTestRun_rejectsHistoricalRevisionEvenWhenStillVisibleInReviewHistory() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        SkillVersion previous = createVersion(99L, 101L, "1.1.0", SkillVersionStatus.SUPERSEDED);

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        null,
                        List.of(active, previous),
                        List.of(),
                        null,
                        null
                )
        );

        assertThatThrownBy(() -> service.createReviewTestRun(
                42L,
                99L,
                new CreateReviewTestRunRequest(ReviewTestRunSource.MANUAL, ReviewTestRunStatus.WARNING, "Manual smoke", "summary", "details", null),
                "admin",
                Map.of()
        )).isInstanceOf(DomainForbiddenException.class);
        verify(reviewTestRunRepository, never()).save(any());
    }

    @Test
    void createReviewTestRun_rejectsClosedReviewStages() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter", ReviewTaskStatus.APPROVED);
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PUBLISHED);

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        active,
                        List.of(active),
                        List.of(),
                        null,
                        null
                )
        );

        assertThatThrownBy(() -> service.createReviewTestRun(
                42L,
                101L,
                new CreateReviewTestRunRequest(ReviewTestRunSource.MANUAL, ReviewTestRunStatus.WARNING, "Manual smoke", "summary", "details", null),
                "admin",
                Map.of()
        )).isInstanceOf(DomainBadRequestException.class);
        verify(reviewTestRunRepository, never()).save(any());
    }

    @Test
    void listReviewCommentThreads_returnsLineScopedThreads() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        ReviewCommentThread thread = createReviewCommentThread(5L, 42L, 101L, "README.md", 7, "admin");
        ReviewComment rootComment = createReviewComment(8L, 5L, "Please clarify the installation step.");
        ReviewComment replyComment = createReviewComment(9L, 5L, "Updated in the latest revision.");
        SkillFile readme = createFile(1L, 101L, "README.md");

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        null,
                        List.of(active),
                        List.of(readme),
                        null,
                        null
                )
        );
        given(skillQueryService.getReviewVersionSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewVersionSnapshotDTO(
                        101L,
                        active,
                        List.of(readme),
                        "README.md",
                        "# demo"
                )
        );
        given(reviewCommentThreadRepository.findByReviewTaskIdAndSkillVersionIdAndFilePathOrderByLineNumberAscCreatedAtAsc(
                42L,
                101L,
                "README.md"
        )).willReturn(List.of(thread));
        given(reviewCommentRepository.findByThreadIdInOrderByCreatedAtAsc(List.of(5L)))
                .willReturn(List.of(rootComment, replyComment));

        List<ReviewCommentThreadResponse> response = service.listReviewCommentThreads(42L, 101L, "README.md", "admin", Map.of());

        assertThat(response).singleElement().satisfies(commentThread -> {
            assertThat(commentThread.lineNumber()).isEqualTo(7);
            assertThat(commentThread.comments()).extracting(ReviewCommentResponse::body)
                    .containsExactly("Please clarify the installation step.", "Updated in the latest revision.");
        });
    }

    @Test
    void createReviewCommentThread_savesRootThreadAndInitialComment() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        SkillFile readme = createFile(1L, 101L, "README.md");
        ReviewCommentThread savedThread = createReviewCommentThread(12L, 42L, 101L, "README.md", 3, "admin");
        ReviewComment savedComment = createReviewComment(13L, 12L, "Please add the missing prerequisites.");

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        null,
                        List.of(active),
                        List.of(readme),
                        "README.md",
                        "# demo"
                )
        );
        given(skillQueryService.getReviewVersionSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewVersionSnapshotDTO(
                        101L,
                        active,
                        List.of(readme),
                        "README.md",
                        "# demo"
                )
        );
        given(skillQueryService.getFileContentByVersionId(101L, "README.md"))
                .willReturn(new ByteArrayInputStream("first\nsecond\nthird".getBytes()));
        given(reviewCommentThreadRepository.save(any(ReviewCommentThread.class))).willReturn(savedThread);
        given(reviewCommentRepository.save(any(ReviewComment.class))).willReturn(savedComment);

        ReviewCommentThreadResponse response = service.createReviewCommentThread(
                42L,
                101L,
                new CreateReviewCommentThreadRequest("README.md", 3, "Please add the missing prerequisites."),
                "admin",
                Map.of()
        );

        assertThat(response.id()).isEqualTo(12L);
        assertThat(response.filePath()).isEqualTo("README.md");
        assertThat(response.comments()).singleElement().satisfies(comment -> {
            assertThat(comment.threadId()).isEqualTo(12L);
            assertThat(comment.body()).isEqualTo("Please add the missing prerequisites.");
        });
    }

    @Test
    void createReviewComment_rejectsThreadsOutsideCurrentReviewTask() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        ReviewCommentThread foreignThread = createReviewCommentThread(15L, 99L, 101L, "README.md", 8, "admin");

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        null,
                        List.of(active),
                        List.of(),
                        null,
                        null
                )
        );
        given(reviewCommentThreadRepository.findById(15L)).willReturn(Optional.of(foreignThread));

        assertThatThrownBy(() -> service.createReviewComment(
                42L,
                15L,
                new CreateReviewCommentRequest("Reply"),
                "admin",
                Map.of()
        )).isInstanceOf(DomainForbiddenException.class);
        verify(reviewCommentRepository, never()).save(any(ReviewComment.class));
    }

    @Test
    void createReviewCommentThread_rejectsHistoricalRevisionOutsideActiveReviewVersion() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        SkillVersion previous = createVersion(99L, 101L, "1.1.0", SkillVersionStatus.SUPERSEDED);

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        null,
                        List.of(active, previous),
                        List.of(),
                        null,
                        null
                )
        );

        assertThatThrownBy(() -> service.createReviewCommentThread(
                42L,
                99L,
                new CreateReviewCommentThreadRequest("README.md", 1, "Old revision comment"),
                "admin",
                Map.of()
        )).isInstanceOf(DomainForbiddenException.class);
        verify(reviewCommentThreadRepository, never()).save(any(ReviewCommentThread.class));
    }

    @Test
    void createReviewCommentThread_rejectsOutOfRangeLineNumbers() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        SkillFile readme = createFile(1L, 101L, "README.md");

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        null,
                        List.of(active),
                        List.of(readme),
                        "README.md",
                        "# demo"
                )
        );
        given(skillQueryService.getReviewVersionSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewVersionSnapshotDTO(
                        101L,
                        active,
                        List.of(readme),
                        "README.md",
                        "# demo"
                )
        );
        given(skillQueryService.getFileContentByVersionId(101L, "README.md"))
                .willReturn(new ByteArrayInputStream("single line".getBytes()));

        assertThatThrownBy(() -> service.createReviewCommentThread(
                42L,
                101L,
                new CreateReviewCommentThreadRequest("README.md", 4, "Out of range"),
                "admin",
                Map.of()
        )).isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        verify(reviewCommentThreadRepository, never()).save(any(ReviewCommentThread.class));
    }

    @Test
    void createReviewCommentThread_rejectsNonPreviewableFiles() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        SkillFile archive = createFile(2L, 101L, "bundle.zip", 200L, "application/zip");

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        null,
                        List.of(active),
                        List.of(archive),
                        null,
                        null
                )
        );
        given(skillQueryService.getReviewVersionSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewVersionSnapshotDTO(
                        101L,
                        active,
                        List.of(archive),
                        null,
                        null
                )
        );

        assertThatThrownBy(() -> service.createReviewCommentThread(
                42L,
                101L,
                new CreateReviewCommentThreadRequest("bundle.zip", 1, "Binary file"),
                "admin",
                Map.of()
        )).isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class);
        verify(reviewCommentThreadRepository, never()).save(any(ReviewCommentThread.class));
    }

    @Test
    void createReviewComment_rejectsHistoricalVersionThread() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");
        Skill skill = createSkill(101L, 20L, "skill-a");
        SkillVersion active = createVersion(101L, 101L, "1.2.0", SkillVersionStatus.PENDING_REVIEW);
        SkillVersion previous = createVersion(99L, 101L, "1.1.0", SkillVersionStatus.SUPERSEDED);
        ReviewCommentThread historicalThread = createReviewCommentThread(16L, 42L, 99L, "README.md", 2, "admin");

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("admin")).willReturn(Set.of("SKILL_ADMIN"));
        given(reviewService.canViewReview(task, "admin", namespace.getType(), Map.of(), Set.of("SKILL_ADMIN"))).willReturn(true);
        given(skillQueryService.getReviewSkillSnapshot(101L)).willReturn(
                new SkillQueryService.ReviewSkillSnapshotDTO(
                        skill,
                        "Owner",
                        active,
                        null,
                        List.of(active, previous),
                        List.of(),
                        null,
                        null
                )
        );
        given(reviewCommentThreadRepository.findById(16L)).willReturn(Optional.of(historicalThread));

        assertThatThrownBy(() -> service.createReviewComment(
                42L,
                16L,
                new CreateReviewCommentRequest("Reply"),
                "admin",
                Map.of()
        )).isInstanceOf(DomainForbiddenException.class);
        verify(reviewCommentRepository, never()).save(any(ReviewComment.class));
    }

    @Test
    void getReviewSkillDetail_rejectsUnauthorizedUser() {
        ReviewTask task = createReviewTask(42L, 101L, 20L, "submitter");
        Namespace namespace = createNamespace(20L, "team-a");

        given(reviewTaskRepository.findById(42L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("user-9")).willReturn(Set.of());
        given(reviewService.canViewReview(task, "user-9", namespace.getType(), Map.of(), Set.of())).willReturn(false);

        assertThatThrownBy(() -> service.getReviewSkillDetail(42L, "user-9", Map.of()))
                .isInstanceOf(DomainForbiddenException.class);
    }

    private ReviewTask createReviewTask(Long reviewId, Long versionId, Long namespaceId, String submittedBy) {
        return createReviewTask(reviewId, versionId, namespaceId, submittedBy, ReviewTaskStatus.PENDING);
    }

    private ReviewTask createReviewTask(Long reviewId,
                                        Long versionId,
                                        Long namespaceId,
                                        String submittedBy,
                                        ReviewTaskStatus status) {
        ReviewTask task = new ReviewTask(versionId, namespaceId, submittedBy);
        setField(task, "id", reviewId);
        setField(task, "status", status);
        return task;
    }

    private Namespace createNamespace(Long id, String slug) {
        Namespace namespace = new Namespace(slug, "Team A", "owner-1");
        setField(namespace, "id", id);
        return namespace;
    }

    private Skill createSkill(Long id, Long namespaceId, String slug) {
        Skill skill = new Skill(namespaceId, slug, "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", id);
        skill.setDisplayName("Skill A");
        skill.setSummary("Summary");
        setField(skill, "downloadCount", 8L);
        setField(skill, "starCount", 2);
        setField(skill, "ratingAvg", new BigDecimal("4.5"));
        setField(skill, "ratingCount", 3);
        return skill;
    }

    private SkillVersion createVersion(Long id, Long skillId, String version, SkillVersionStatus status) {
        SkillVersion skillVersion = new SkillVersion(skillId, version, "owner-1");
        setField(skillVersion, "id", id);
        setField(skillVersion, "status", status);
        setField(skillVersion, "publishedAt", Instant.parse("2026-03-19T00:00:00Z"));
        setField(skillVersion, "createdAt", Instant.parse("2026-03-19T00:00:00Z"));
        setField(skillVersion, "fileCount", 1);
        setField(skillVersion, "totalSize", 100L);
        return skillVersion;
    }

    private SkillFile createFile(Long id, Long versionId, String filePath) {
        return createFile(id, versionId, filePath, 123L, "text/markdown");
    }

    private SkillFile createFile(Long id, Long versionId, String filePath, Long fileSize, String contentType) {
        SkillFile file = new SkillFile(versionId, filePath, fileSize, contentType, "sha", "skills/demo/" + filePath);
        setField(file, "id", id);
        return file;
    }

    private ReviewTestRun createReviewTestRun(Long id,
                                              Long reviewTaskId,
                                              Long versionId,
                                              ReviewTestRunSource source,
                                              ReviewTestRunStatus status,
                                              String name) {
        ReviewTestRun reviewTestRun = new ReviewTestRun(
                reviewTaskId,
                versionId,
                source,
                status,
                name,
                "summary",
                "details",
                "https://ci.example.com/run/" + id,
                "admin"
        );
        setField(reviewTestRun, "id", id);
        setField(reviewTestRun, "createdAt", Instant.parse("2026-04-02T09:00:00Z"));
        return reviewTestRun;
    }

    private ReviewCommentThread createReviewCommentThread(Long id,
                                                          Long reviewTaskId,
                                                          Long versionId,
                                                          String filePath,
                                                          Integer lineNumber,
                                                          String createdBy) {
        ReviewCommentThread reviewCommentThread = new ReviewCommentThread(reviewTaskId, versionId, filePath, lineNumber, createdBy);
        setField(reviewCommentThread, "id", id);
        setField(reviewCommentThread, "createdAt", Instant.parse("2026-04-02T11:00:00Z"));
        return reviewCommentThread;
    }

    private ReviewComment createReviewComment(Long id, Long threadId, String body) {
        ReviewComment reviewComment = new ReviewComment(threadId, body, "admin");
        setField(reviewComment, "id", id);
        setField(reviewComment, "createdAt", Instant.parse("2026-04-02T11:05:00Z"));
        return reviewComment;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
