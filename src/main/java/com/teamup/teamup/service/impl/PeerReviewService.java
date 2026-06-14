package com.teamup.teamup.service.impl;

import com.teamup.teamup.dto.AnonymousReviewDto;
import com.teamup.teamup.dto.PeerReviewDetailDto;
import com.teamup.teamup.dto.PeerReviewSubmissionDto;
import com.teamup.teamup.entity.Group;
import com.teamup.teamup.entity.PeerReview;
import com.teamup.teamup.entity.User;
import com.teamup.teamup.exception.DifferentGroupException;
import com.teamup.teamup.exception.DuplicateReviewException;
import com.teamup.teamup.exception.ResourceNotFoundException;
import com.teamup.teamup.exception.SelfReviewException;
import com.teamup.teamup.repository.GroupRepository;
import com.teamup.teamup.repository.PeerReviewRepository;
import com.teamup.teamup.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for anonymous peer review submission and retrieval.
 *
 * <h3>Anonymity guarantee</h3>
 * When returning reviews to a ROLE_STUDENT, the service ALWAYS uses
 * {@link AnonymousReviewDto} — the reviewer ID and identity fields are
 * completely absent from the payload.
 *
 * {@link PeerReviewDetailDto} (full identity) is used ONLY when the caller
 * has ROLE_TEACHER or ROLE_ADMIN.
 *
 * <h3>Security enforcement order</h3>
 * <pre>
 * 1. Validate: reviewer ≠ reviewee  (SelfReviewException)
 * 2. Validate: both in same group    (DifferentGroupException)
 * 3. Validate: no duplicate review   (DuplicateReviewException)
 * 4. Save → AnonymousReviewDto (students) | PeerReviewDetailDto (teachers)
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PeerReviewService {

    private final PeerReviewRepository peerReviewRepository;
    private final GroupRepository     groupRepository;
    private final UserRepository      userRepository;

    // ══════════════════════════════════════════════════════════════════════════════
    // Submit a review
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Submits a peer review.
     *
     * @param dto       the review payload (groupId, revieweeId, score 1–5, comment)
     * @param reviewer  the authenticated user submitting the review
     * @return the submitted review as an AnonymousReviewDto (reviewer identity hidden)
     * @throws SelfReviewException        if reviewer == reviewee
     * @throws DifferentGroupException    if they are not in the same group
     * @throws DuplicateReviewException   if a review already exists for this pair
     */
    public AnonymousReviewDto submitReview(
            PeerReviewSubmissionDto dto,
            User reviewer) {

        log.info("Peer review: reviewer={}, reviewee={}, group={}",
            reviewer.getId(), dto.getRevieweeId(), dto.getGroupId());

        // ── 1. Self-review check ───────────────────────────────────────────────
        if (reviewer.getId().equals(dto.getRevieweeId())) {
            throw new SelfReviewException();
        }

        // ── 2. Both in the same group ─────────────────────────────────────────
        User reviewee = userRepository.findById(dto.getRevieweeId())
            .orElseThrow(() -> new ResourceNotFoundException("User", dto.getRevieweeId()));

        if (!areInSameGroup(reviewer, reviewee, dto.getGroupId())) {
            log.warn("DifferentGroupException: reviewer {} and reviewee {} not in same group {}",
                reviewer.getId(), reviewee.getId(), dto.getGroupId());
            throw new DifferentGroupException();
        }

        // ── 3. No duplicate review ─────────────────────────────────────────────
        if (peerReviewRepository.existsByGroupAndReviewerAndReviewee(
                dto.getGroupId(), reviewer.getId(), reviewee.getId())) {
            throw new DuplicateReviewException();
        }

        // ── 4. Save ───────────────────────────────────────────────────────────
        Group group = groupRepository.findById(dto.getGroupId())
            .orElseThrow(() -> new ResourceNotFoundException("Group", dto.getGroupId()));

        PeerReview review = PeerReview.builder()
            .score(dto.getScore())
            .comment(dto.getComment())
            .reviewedAt(LocalDateTime.now())
            .group(group)
            .reviewer(reviewer)
            .reviewee(reviewee)
            .build();

        review = peerReviewRepository.save(review);
        log.info("Peer review saved: id={}, reviewer={}, reviewee={}",
            review.getId(), reviewer.getId(), reviewee.getId());

        // ── 5. Return ANONYMOUS DTO (reviewer identity stripped) ──────────────
        return toAnonymousDto(review);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Read reviews — anonymous for students, full detail for teachers
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Returns all reviews received by a student in a group.
     * Returns ONLY AnonymousReviewDto — reviewer identity is NEVER exposed.
     *
     * @param groupId    the group to query
     * @param revieweeId the student requesting their reviews
     * @return list of anonymous review entries
     */
    @Transactional(readOnly = true)
    public List<AnonymousReviewDto> getReviewsReceivedByStudent(Long groupId, Long revieweeId) {
        return peerReviewRepository.findReceivedReviews(groupId, revieweeId)
            .stream()
            .map(this::toAnonymousDto)
            .toList();
    }

    /**
     * Returns ALL reviews in a group with FULL detail (teacher/admin only).
     * The teacher sees reviewer identity, reviewee identity, scores, and comments.
     *
     * @param groupId the group to query
     * @return list of detail DTOs with full identity
     */
    @Transactional(readOnly = true)
    public List<PeerReviewDetailDto> getAllReviewsInGroup(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }
        return peerReviewRepository.findAllByGroupIdWithUsers(groupId)
            .stream()
            .map(this::toDetailDto)
            .toList();
    }

    /**
     * Returns the average attitude score for each member in a group.
     * Used by the report generation service.
     *
     * @param groupId   the group to analyse
     * @param memberIds all member user IDs in the group
     * @return map of userId → rounded average score (e.g. 4.3) or null if no reviews
     */
    @Transactional(readOnly = true)
    public Map<Long, Double> getAverageScoresPerMember(Long groupId, List<Long> memberIds) {
        return memberIds.stream()
            .collect(java.util.stream.Collectors.toMap(
                id -> id,
                id -> peerReviewRepository.averageScoreForMember(groupId, id)
            ));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Returns true if both users are members of the given group.
     * Checks both the group → members set and the inverse member → groups set.
     */
    private boolean areInSameGroup(User reviewer, User reviewee, Long groupId) {
        // Option A: check via the reviewer
        boolean reviewerInGroup = reviewer.getGroups().stream()
            .anyMatch(g -> g.getId().equals(groupId));

        // Option B: check via the reviewee
        boolean revieweeInGroup = reviewee.getGroups().stream()
            .anyMatch(g -> g.getId().equals(groupId));

        return reviewerInGroup && revieweeInGroup;
    }

    /**
     * Maps a PeerReview entity → AnonymousReviewDto.
     * Reviewer identity fields are NEVER included.
     *
     * The memberPosition is computed as the 1-based index of the reviewee
     * within the group's member list, so the report can say "Member #2"
     * without revealing the actual name.
     */
    private AnonymousReviewDto toAnonymousDto(PeerReview review) {
        int position = computeMemberPosition(review.getGroup(), review.getReviewee());

        return AnonymousReviewDto.builder()
            .memberPosition(position)              // anonymous identifier only
            .score(review.getScore())
            .attitudeLabel(review.attitudeLabel())
            .comment(review.getComment())
            .reviewedAt(review.getReviewedAt())
            // reviewerId, reviewerName, reviewerEmail — STRIPPED
            .build();
    }

    /**
     * Maps a PeerReview entity → PeerReviewDetailDto with full identity.
     * Only used for teacher/admin endpoints.
     */
    private PeerReviewDetailDto toDetailDto(PeerReview review) {
        return PeerReviewDetailDto.builder()
            .reviewId(review.getId())
            .reviewerId(review.getReviewer().getId())
            .reviewerName(review.getReviewer().getName())
            .reviewerEmail(review.getReviewer().getEmail())
            .revieweeId(review.getReviewee().getId())
            .revieweeName(review.getReviewee().getName())
            .score(review.getScore())
            .attitudeLabel(review.attitudeLabel())
            .comment(review.getComment())
            .reviewedAt(review.getReviewedAt())
            .groupId(review.getGroup().getId())
            .groupName(review.getGroup().getGroupName())
            .build();
    }

    /**
     * Computes the 1-based position of a user within the group's member list.
     * Used as an anonymous identifier in reports.
     */
    private int computeMemberPosition(Group group, User reviewee) {
        AtomicInteger pos = new AtomicInteger(0);
        group.getMembers().stream()
            .sorted((a, b) -> a.getId().compareTo(b.getId()))
            .forEach(member -> {
                if (member.getId().equals(reviewee.getId())) {
                    pos.set(group.getMembers().stream()
                        .sorted((a, b) -> a.getId().compareTo(b.getId()))
                        .toList()
                        .indexOf(member) + 1);
                }
            });
        return pos.get();
    }
}
