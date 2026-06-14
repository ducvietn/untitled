package com.teamup.teamup.controller;

import com.teamup.teamup.dto.AnonymousReviewDto;
import com.teamup.teamup.dto.PeerReviewDetailDto;
import com.teamup.teamup.dto.PeerReviewSubmissionDto;
import com.teamup.teamup.entity.User;
import com.teamup.teamup.exception.ApiResponse;
import com.teamup.teamup.exception.ResourceNotFoundException;
import com.teamup.teamup.repository.UserRepository;
import com.teamup.teamup.security.CustomUserDetails;
import com.teamup.teamup.service.impl.PeerReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for peer review operations.
 *
 * <h3>DTO selection rule (enforced here, not just in the frontend)</h3>
 * <ul>
 *   <li>ROLE_STUDENT → {@link AnonymousReviewDto} — reviewer identity NEVER exposed</li>
 *   <li>ROLE_TEACHER / ROLE_ADMIN → {@link PeerReviewDetailDto} — full detail</li>
 * </ul>
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>POST</td><td>/reviews</td><td>Student</td><td>AnonymousReviewDto</td></tr>
 *   <tr><td>GET</td><td>/reviews/group/{groupId}/received</td><td>Student</td><td>List&lt;AnonymousReviewDto&gt;</td></tr>
 *   <tr><td>GET</td><td>/reviews/group/{groupId}</td><td>Teacher</td><td>List&lt;PeerReviewDetailDto&gt;</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Slf4j
public class ReviewController {

    private final PeerReviewService peerReviewService;
    private final UserRepository   userRepository;

    // ══════════════════════════════════════════════════════════════════════════════
    // Submit a review — always returns AnonymousReviewDto (reviewer hidden)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/reviews
     *
     * Business rules enforced by PeerReviewService:
     * - reviewer ≠ reviewee
     * - both belong to the same group
     * - score ∈ [1, 5]
     * - no duplicate review for the same pair
     *
     * Response is ALWAYS an {@link AnonymousReviewDto}.
     */
    @PostMapping
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AnonymousReviewDto>> submitReview(
            @Valid @RequestBody PeerReviewSubmissionDto dto,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        log.info("Review submission: reviewer={}, reviewee={}, group={}",
            currentUser.getUserId(), dto.getRevieweeId(), dto.getGroupId());

        // Load the full User entity with groups eagerly for group-membership validation.
        // Without this, areInSameGroup() would NPE on a null groups set.
        User reviewer = userRepository.findById(currentUser.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User", currentUser.getUserId()));

        AnonymousReviewDto result = peerReviewService.submitReview(dto, reviewer);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Peer review submitted.", result));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Read reviews — anonymous for students
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/reviews/group/{groupId}/received
     *
     * Returns all reviews received by the authenticated student.
     * Response is ALWAYS a list of {@link AnonymousReviewDto}.
     */
    @GetMapping("/group/{groupId}/received")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AnonymousReviewDto>>> getReviewsReceived(
            @PathVariable Long groupId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        log.debug("Fetching anonymous reviews for student {} in group {}",
            currentUser.getUserId(), groupId);

        List<AnonymousReviewDto> reviews =
            peerReviewService.getReviewsReceivedByStudent(groupId, currentUser.getUserId());

        return ResponseEntity.ok(ApiResponse.ok(reviews));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Read reviews — full detail for teachers
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/reviews/group/{groupId}
     *
     * Returns all reviews in a group with FULL detail.
     * Available ONLY to ROLE_TEACHER or ROLE_ADMIN.
     */
    @GetMapping("/group/{groupId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<PeerReviewDetailDto>>> getAllReviewsInGroup(
            @PathVariable Long groupId) {

        log.info("Teacher fetching all reviews in group {}", groupId);

        List<PeerReviewDetailDto> reviews =
            peerReviewService.getAllReviewsInGroup(groupId);

        return ResponseEntity.ok(ApiResponse.ok(reviews));
    }
}
