package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewPermissionChecker;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.dto.ReviewCommentResponse;
import com.iflytek.skillhub.dto.ReviewCommentThreadResponse;
import com.iflytek.skillhub.dto.ReviewTaskResponse;
import com.iflytek.skillhub.dto.ReviewSkillDetailResponse;
import com.iflytek.skillhub.dto.ReviewTestRunResponse;
import com.iflytek.skillhub.dto.SkillDetailResponse;
import com.iflytek.skillhub.dto.SkillFileResponse;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillVersionResponse;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import com.iflytek.skillhub.service.ReviewSkillDetailAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewPortalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private ReviewTaskRepository reviewTaskRepository;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @MockBean
    private com.iflytek.skillhub.domain.namespace.NamespaceRepository namespaceRepository;

    @MockBean
    private GovernanceQueryRepository governanceQueryRepository;

    @MockBean
    private RbacService rbacService;

    @MockBean
    private ReviewPermissionChecker permissionChecker;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private ReviewSkillDetailAppService reviewSkillDetailAppService;

    @Test
    void submitReview_passesNamespaceRolesToService() throws Exception {
        ReviewTask task = createReviewTask(1L, 20L, "user-1");
        stubNamespaceRoles("user-1", List.of(new NamespaceMember(20L, "user-1", NamespaceRole.MEMBER)));
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());
        given(reviewService.submitReview(100L, "user-1", Map.of(20L, NamespaceRole.MEMBER), Set.of())).willReturn(task);
        stubReviewResponse(task);

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillVersionId\":100}")
                        .with(csrf())
                        .with(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    void listPendingReviews_allowsNamespaceMember() throws Exception {
        Namespace namespace = createNamespace(20L, "team-a");
        stubNamespaceRoles("user-1", List.of(new NamespaceMember(20L, "user-1", NamespaceRole.MEMBER)));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());
        given(reviewService.canReviewNamespace(
                any(ReviewTask.class),
                eq("user-1"),
                eq(namespace.getType()),
                eq(Map.of(20L, NamespaceRole.MEMBER)),
                eq(Set.of()))).willReturn(true);
        var task = createReviewTask(1L, 20L, "user-2");
        given(reviewTaskRepository.findByNamespaceIdAndStatus(eq(20L), eq(ReviewTaskStatus.PENDING), any()))
                .willReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 20), 1));
        stubReviewResponse(task);

        mockMvc.perform(get("/api/v1/reviews/pending")
                        .param("namespaceId", "20")
                        .with(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(1L));
    }

    @Test
    void getReviewDetail_allowsSubmitter() throws Exception {
        ReviewTask task = createReviewTask(1L, 20L, "user-1");
        Namespace namespace = createNamespace(20L, "team-a");
        stubNamespaceRoles("user-1", List.of());
        given(reviewTaskRepository.findById(1L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("user-1")).willReturn(Set.of());
        given(reviewService.canViewReview(task, "user-1", namespace.getType(), Map.of(), Set.of())).willReturn(true);
        stubReviewResponse(task);

        mockMvc.perform(get("/api/v1/reviews/1").with(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.submittedBy").value("user-1"));
    }

    @Test
    void getReviewDetail_forbidsUnrelatedUser() throws Exception {
        ReviewTask task = createReviewTask(1L, 20L, "user-1");
        Namespace namespace = createNamespace(20L, "team-a");
        stubNamespaceRoles("user-9", List.of());
        given(reviewTaskRepository.findById(1L)).willReturn(Optional.of(task));
        given(namespaceRepository.findById(20L)).willReturn(Optional.of(namespace));
        given(rbacService.getUserRoleCodes("user-9")).willReturn(Set.of());
        given(reviewService.canViewReview(task, "user-9", namespace.getType(), Map.of(), Set.of())).willReturn(false);

        mockMvc.perform(get("/api/v1/reviews/1").with(auth("user-9")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void getReviewSkillDetail_returnsReviewBoundPayload() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(reviewSkillDetailAppService.getReviewSkillDetail(1L, "admin", Map.of()))
                .willReturn(new ReviewSkillDetailResponse(
                        new SkillDetailResponse(
                                30L,
                                "skill-a",
                                "Skill A",
                                "owner-1",
                                "Owner",
                                "Summary",
                                "PUBLIC",
                                "ACTIVE",
                                8L,
                                2,
                                null,
                                0,
                                false,
                                "team-a",
                                List.<com.iflytek.skillhub.dto.SkillLabelDto>of(),
                                false,
                                false,
                                false,
                                false,
                                new SkillLifecycleVersionResponse(100L, "1.2.0", "PENDING_REVIEW"),
                                new SkillLifecycleVersionResponse(99L, "1.1.0", "PUBLISHED"),
                                new SkillLifecycleVersionResponse(100L, "1.2.0", "PENDING_REVIEW"),
                                null,
                                "REVIEW_TASK"
                        ),
                        List.of(new SkillVersionResponse(100L, "1.2.0", "PENDING_REVIEW", null, 1, 10L, null, true)),
                        List.of(new SkillFileResponse(1L, "README.md", 123L, "text/markdown", "sha")),
                        "README.md",
                        "# demo",
                        "/api/v1/reviews/1/download",
                        "1.2.0"
                ));

        mockMvc.perform(get("/api/v1/reviews/1/skill-detail").with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeVersion").value("1.2.0"))
                .andExpect(jsonPath("$.data.downloadUrl").value("/api/v1/reviews/1/download"))
                .andExpect(jsonPath("$.data.files[0].filePath").value("README.md"));
    }

    @Test
    void getReviewSkillDetail_forbidsUnauthorizedUser() throws Exception {
        stubNamespaceRoles("user-9", List.of());
        given(reviewSkillDetailAppService.getReviewSkillDetail(1L, "user-9", Map.of()))
                .willThrow(new com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException("review.no_permission"));

        mockMvc.perform(get("/api/v1/reviews/1/skill-detail").with(auth("user-9")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void listReviewCommentThreads_returnsLineScopedComments() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(reviewSkillDetailAppService.listReviewCommentThreads(1L, 100L, "README.md", "admin", Map.of()))
                .willReturn(List.of(new ReviewCommentThreadResponse(
                        9L,
                        1L,
                        100L,
                        "README.md",
                        12,
                        "admin",
                        java.time.Instant.parse("2026-04-02T09:30:00Z"),
                        List.of(
                                new ReviewCommentResponse(
                                        10L,
                                        9L,
                                        "Please add prerequisites.",
                                        "admin",
                                        java.time.Instant.parse("2026-04-02T09:31:00Z")
                                )
                        )
                )));

        mockMvc.perform(get("/api/v1/reviews/1/versions/100/comments")
                        .param("filePath", "README.md")
                        .with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].lineNumber").value(12))
                .andExpect(jsonPath("$.data[0].comments[0].body").value("Please add prerequisites."));
    }

    @Test
    void createReviewCommentThread_passesRequestBodyToService() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(reviewSkillDetailAppService.createReviewCommentThread(
                1L,
                100L,
                new com.iflytek.skillhub.dto.CreateReviewCommentThreadRequest(
                        "README.md",
                        12,
                        "Please add prerequisites."
                ),
                "admin",
                Map.of()
        )).willReturn(new ReviewCommentThreadResponse(
                9L,
                1L,
                100L,
                "README.md",
                12,
                "admin",
                java.time.Instant.parse("2026-04-02T09:30:00Z"),
                List.of(
                        new ReviewCommentResponse(
                                10L,
                                9L,
                                "Please add prerequisites.",
                                "admin",
                                java.time.Instant.parse("2026-04-02T09:31:00Z")
                        )
                )
        ));

        mockMvc.perform(post("/api/v1/reviews/1/versions/100/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "filePath":"README.md",
                                  "lineNumber":12,
                                  "body":"Please add prerequisites."
                                }
                                """)
                        .with(csrf())
                        .with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(9L))
                .andExpect(jsonPath("$.data.comments[0].body").value("Please add prerequisites."));
    }

    @Test
    void createReviewComment_passesRequestBodyToService() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(reviewSkillDetailAppService.createReviewComment(
                1L,
                9L,
                new com.iflytek.skillhub.dto.CreateReviewCommentRequest("Updated."),
                "admin",
                Map.of()
        )).willReturn(new ReviewCommentResponse(
                11L,
                9L,
                "Updated.",
                "admin",
                java.time.Instant.parse("2026-04-02T09:32:00Z")
        ));

        mockMvc.perform(post("/api/v1/reviews/1/comment-threads/9/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "body":"Updated."
                                }
                                """)
                        .with(csrf())
                        .with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(11L))
                .andExpect(jsonPath("$.data.threadId").value(9L));
    }

    @Test
    void getReviewTestRuns_returnsRevisionArtifacts() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(reviewSkillDetailAppService.listReviewTestRuns(1L, 100L, "admin", Map.of()))
                .willReturn(List.of(new ReviewTestRunResponse(
                        7L,
                        100L,
                        "CI",
                        "PASSED",
                        "CI smoke",
                        "All checks passed",
                        "details",
                        "https://ci.example.com/run/7",
                        "admin",
                        java.time.Instant.parse("2026-04-02T09:00:00Z")
                )));

        mockMvc.perform(get("/api/v1/reviews/1/versions/100/test-runs").with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("CI smoke"))
                .andExpect(jsonPath("$.data[0].status").value("PASSED"));
    }

    @Test
    void createReviewTestRun_passesRequestBodyToService() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(reviewSkillDetailAppService.createReviewTestRun(
                1L,
                100L,
                new com.iflytek.skillhub.dto.CreateReviewTestRunRequest(
                        com.iflytek.skillhub.domain.review.ReviewTestRunSource.MANUAL,
                        com.iflytek.skillhub.domain.review.ReviewTestRunStatus.WARNING,
                        "Manual smoke",
                        "Found one issue",
                        "details",
                        "https://ci.example.com/run/8"
                ),
                "admin",
                Map.of()
        )).willReturn(new ReviewTestRunResponse(
                8L,
                100L,
                "MANUAL",
                "WARNING",
                "Manual smoke",
                "Found one issue",
                "details",
                "https://ci.example.com/run/8",
                "admin",
                java.time.Instant.parse("2026-04-02T10:00:00Z")
        ));

        mockMvc.perform(post("/api/v1/reviews/1/versions/100/test-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source":"MANUAL",
                                  "status":"WARNING",
                                  "name":"Manual smoke",
                                  "summary":"Found one issue",
                                  "detailsMarkdown":"details",
                                  "externalUrl":"https://ci.example.com/run/8"
                                }
                                """)
                        .with(csrf())
                        .with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(8L))
                .andExpect(jsonPath("$.data.source").value("MANUAL"));
    }

    @Test
    void listReviews_appliesRequestedTimeSortDirection() throws Exception {
        stubNamespaceRoles("admin", List.of());
        PageRequest pageable = PageRequest.of(
                1,
                5,
                Sort.by(
                        new Sort.Order(Sort.Direction.ASC, "reviewedAt"),
                        new Sort.Order(Sort.Direction.ASC, "id")
                )
        );
        given(reviewTaskRepository.findByStatus(ReviewTaskStatus.APPROVED, pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        mockMvc.perform(get("/api/v1/reviews")
                        .param("status", "APPROVED")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sortDirection", "ASC")
                        .with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(reviewTaskRepository).findByStatus(ReviewTaskStatus.APPROVED, pageable);
    }

    @Test
    void downloadReviewVersion_streamsZipForAuthorizedReviewer() throws Exception {
        stubNamespaceRoles("admin", List.of());
        given(reviewSkillDetailAppService.downloadReviewPackage(1L, "admin", Map.of()))
                .willReturn(new SkillDownloadService.DownloadResult(
                        () -> new java.io.ByteArrayInputStream("zip".getBytes()),
                        "skill-a-1.2.0.zip",
                        3L,
                        "application/zip",
                        null,
                        true
                ));

        mockMvc.perform(get("/api/v1/reviews/1/download").with(auth("admin")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", org.hamcrest.Matchers.containsString("skill-a-1.2.0.zip")));
    }

    private void stubReviewResponse(ReviewTask task) {
        ReviewTaskResponse response = toReviewTaskResponse(task);
        given(governanceQueryRepository.getReviewTaskResponse(task)).willReturn(response);
        given(governanceQueryRepository.getReviewTaskResponses(anyList()))
                .willAnswer(invocation -> ((List<ReviewTask>) invocation.getArgument(0)).stream()
                        .map(this::toReviewTaskResponse)
                        .toList());
    }

    private ReviewTaskResponse toReviewTaskResponse(ReviewTask task) {
        return new ReviewTaskResponse(
                task.getId(),
                task.getSkillVersionId(),
                "team-a",
                "skill-a",
                "1.0.0",
                task.getStatus().name(),
                task.getSubmittedBy(),
                "Submitter",
                task.getReviewedBy(),
                null,
                task.getReviewComment(),
                task.getSubmittedAt(),
                task.getReviewedAt()
        );
    }

    private void stubNamespaceRoles(String userId, List<NamespaceMember> members) {
        given(namespaceMemberRepository.findByUserId(userId)).willReturn(members);
    }

    private RequestPostProcessor auth(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                "",
                "session",
                Set.of()
        );
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        return authentication(authenticationToken);
    }

    private ReviewTask createReviewTask(Long id, Long namespaceId, String submittedBy) {
        ReviewTask task = new ReviewTask(100L, namespaceId, submittedBy);
        setField(task, "id", id);
        setField(task, "status", ReviewTaskStatus.PENDING);
        return task;
    }

    private Namespace createNamespace(Long id, String slug) {
        Namespace namespace = new Namespace(slug, "Team", "owner-1");
        setField(namespace, "id", id);
        return namespace;
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
