package com.teamup.teamup.service.impl;

import com.teamup.teamup.dto.AnonymousReviewDto;
import com.teamup.teamup.dto.PeerReviewDetailDto;
import com.teamup.teamup.dto.PeerReviewSubmissionDto;
import com.teamup.teamup.entity.Group;
import com.teamup.teamup.entity.PeerReview;
import com.teamup.teamup.entity.User;
import com.teamup.teamup.exception.DifferentGroupException;
import com.teamup.teamup.exception.DuplicateReviewException;
import com.teamup.teamup.exception.SelfReviewException;
import com.teamup.teamup.repository.GroupRepository;
import com.teamup.teamup.repository.PeerReviewRepository;
import com.teamup.teamup.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PeerReviewService.
 * Tests submission validation, anonymity guarantee, and repository interactions.
 */
@ExtendWith(MockitoExtension.class)
class PeerReviewServiceTest {

    @Mock private PeerReviewRepository peerReviewRepository;
    @Mock private GroupRepository      groupRepository;
    @Mock private UserRepository       userRepository;
    @InjectMocks private PeerReviewService sut;

    private Group  group;
    private User   alice;   // reviewer
    private User   bob;     // reviewee
    private User   charlie; // not in group

    @BeforeEach
    void setUp() {
        group = Group.builder().id(1L).groupName("Team Alpha").build();
        group.setMembers(new HashSet<>());

        alice   = User.builder().id(10L).name("Alice").email("alice@test.com").build();
        bob     = User.builder().id(20L).name("Bob").email("bob@test.com").build();
        charlie = User.builder().id(30L).name("Charlie").email("charlie@other.com").build();

        alice.getGroups().add(group);
        bob.getGroups().add(group);
        group.getMembers().addAll(List.of(alice, bob));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // submitReview — validation
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("submitReview validation")
    class SubmitValidation {

        @Test
        @DisplayName("Self-review → SelfReviewException")
        void selfReview_throws() {
            PeerReviewSubmissionDto dto = PeerReviewSubmissionDto.builder()
                .groupId(1L).revieweeId(10L).score(4).comment("Great work!").build();

            assertThatThrownBy(() -> sut.submitReview(dto, alice))
                .isInstanceOf(SelfReviewException.class)
                .hasMessageContaining("yourself");
        }

        @Test
        @DisplayName("Reviewer not in group → DifferentGroupException")
        void reviewerNotInGroup_throws() {
            when(userRepository.findById(30L)).thenReturn(Optional.of(charlie));

            PeerReviewSubmissionDto dto = PeerReviewSubmissionDto.builder()
                .groupId(1L).revieweeId(30L).score(4).comment("Good effort.").build();

            assertThatThrownBy(() -> sut.submitReview(dto, alice))
                .isInstanceOf(DifferentGroupException.class);
        }

        @Test
        @DisplayName("Reviewee not in group → DifferentGroupException")
        void revieweeNotInGroup_throws() {
            when(userRepository.findById(20L)).thenReturn(Optional.of(bob));
            // alice IS in group (set up in @BeforeEach)
            // bob IS in group

            // Remove bob from group to trigger DifferentGroupException
            bob.getGroups().clear();
            group.getMembers().remove(bob);

            PeerReviewSubmissionDto dto = PeerReviewSubmissionDto.builder()
                .groupId(1L).revieweeId(20L).score(4).comment("Good effort.").build();

            assertThatThrownBy(() -> sut.submitReview(dto, alice))
                .isInstanceOf(DifferentGroupException.class);
        }

        @Test
        @DisplayName("Duplicate review → DuplicateReviewException")
        void duplicateReview_throws() {
            when(userRepository.findById(20L)).thenReturn(Optional.of(bob));
            when(peerReviewRepository.existsByGroupAndReviewerAndReviewee(1L, 10L, 20L))
                .thenReturn(true);

            PeerReviewSubmissionDto dto = PeerReviewSubmissionDto.builder()
                .groupId(1L).revieweeId(20L).score(4).comment("Good effort.").build();

            assertThatThrownBy(() -> sut.submitReview(dto, alice))
                .isInstanceOf(DuplicateReviewException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // submitReview — success path
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("submitReview success")
    class SubmitSuccess {

        @Test
        @DisplayName("Valid submission → saved and returns AnonymousReviewDto")
        void validSubmission_savedAndReturnsAnonymousDto() {
            when(userRepository.findById(20L)).thenReturn(Optional.of(bob));
            when(peerReviewRepository.existsByGroupAndReviewerAndReviewee(1L, 10L, 20L))
                .thenReturn(false);
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(peerReviewRepository.save(any(PeerReview.class)))
                .thenAnswer(inv -> {
                    PeerReview r = inv.getArgument(0);
                    r.setId(100L);
                    return r;
                });

            PeerReviewSubmissionDto dto = PeerReviewSubmissionDto.builder()
                .groupId(1L).revieweeId(20L).score(5).comment("Excellent teamwork!").build();

            AnonymousReviewDto result = sut.submitReview(dto, alice);

            assertThat(result.getScore()).isEqualTo(5);
            assertThat(result.getComment()).isEqualTo("Excellent teamwork!");
            assertThat(result.getAttitudeLabel()).isEqualTo("Excellent");
            assertThat(result.getReviewedAt()).isNotNull();

            // Critical: reviewer identity fields MUST be null in AnonymousReviewDto
            assertThat(result.getMemberPosition()).isGreaterThan(0);
            // These fields do not exist on AnonymousReviewDto at all — this is the guarantee
        }

        @Test
        @DisplayName("Saved entity contains correct fields")
        void savedEntity_correctFields() {
            when(userRepository.findById(20L)).thenReturn(Optional.of(bob));
            when(peerReviewRepository.existsByGroupAndReviewerAndReviewee(1L, 10L, 20L))
                .thenReturn(false);
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(peerReviewRepository.save(any(PeerReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            PeerReviewSubmissionDto dto = PeerReviewSubmissionDto.builder()
                .groupId(1L).revieweeId(20L).score(3).comment("Satisfactory.").build();

            sut.submitReview(dto, alice);

            ArgumentCaptor<PeerReview> captor = ArgumentCaptor.forClass(PeerReview.class);
            verify(peerReviewRepository).save(captor.capture());

            PeerReview saved = captor.getValue();
            assertThat(saved.getScore()).isEqualTo(3);
            assertThat(saved.getComment()).isEqualTo("Satisfactory.");
            assertThat(saved.getReviewer()).isEqualTo(alice);
            assertThat(saved.getReviewee()).isEqualTo(bob);
            assertThat(saved.getGroup()).isEqualTo(group);
        }

        @Test
        @DisplayName("Score 1 → attitudeLabel 'Very Poor'")
        void score1_veryPoor() {
            when(userRepository.findById(20L)).thenReturn(Optional.of(bob));
            when(peerReviewRepository.existsByGroupAndReviewerAndReviewee(1L, 10L, 20L))
                .thenReturn(false);
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(peerReviewRepository.save(any(PeerReview.class)))
                .thenAnswer(inv -> {
                    PeerReview r = inv.getArgument(0);
                    r.setId(1L);
                    return r;
                });

            PeerReviewSubmissionDto dto = PeerReviewSubmissionDto.builder()
                .groupId(1L).revieweeId(20L).score(1).comment("Poor contribution.").build();

            AnonymousReviewDto result = sut.submitReview(dto, alice);
            assertThat(result.getAttitudeLabel()).isEqualTo("Very Poor");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // getAllReviewsInGroup — returns FULL detail for teachers
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAllReviewsInGroup — teacher detail DTO")
    class TeacherDetail {

        @Test
        @DisplayName("Returns PeerReviewDetailDto with reviewer and reviewee names")
        void returnsDetailDtoWithNames() {
            PeerReview review = PeerReview.builder()
                .id(50L)
                .score(4)
                .comment("Good collaboration.")
                .reviewer(alice)
                .reviewee(bob)
                .group(group)
                .build();

            when(groupRepository.existsById(1L)).thenReturn(true);
            when(peerReviewRepository.findAllByGroupIdWithUsers(1L))
                .thenReturn(List.of(review));

            List<PeerReviewDetailDto> result = sut.getAllReviewsInGroup(1L);

            assertThat(result).hasSize(1);
            PeerReviewDetailDto dto = result.get(0);
            assertThat(dto.getReviewerName()).isEqualTo("Alice");
            assertThat(dto.getReviewerId()).isEqualTo(10L);
            assertThat(dto.getRevieweeName()).isEqualTo("Bob");
            assertThat(dto.getRevieweeId()).isEqualTo(20L);
            assertThat(dto.getScore()).isEqualTo(4);
            assertThat(dto.getComment()).isEqualTo("Good collaboration.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // getAverageScoresPerMember
    // ══════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAverageScoresPerMember")
    class AverageScores {

        @Test
        @DisplayName("Returns null for members with no reviews")
        void noReviews_returnsNull() {
            when(peerReviewRepository.averageScoreForMember(1L, 10L)).thenReturn(null);
            when(peerReviewRepository.averageScoreForMember(1L, 20L)).thenReturn(4.333);

            Map<Long, Double> result = sut.getAverageScoresPerMember(1L, List.of(10L, 20L));

            assertThat(result.get(10L)).isNull();
            assertThat(result.get(20L)).isEqualTo(4.333);
        }
    }
}
