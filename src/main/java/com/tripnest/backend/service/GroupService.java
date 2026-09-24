package com.tripnest.backend.service;

import com.tripnest.backend.dto.GroupRequest;
import com.tripnest.backend.dto.GroupMemberResponse;
import com.tripnest.backend.dto.GroupResponse;
import com.tripnest.backend.entity.Group;
import com.tripnest.backend.entity.GroupMember;
import com.tripnest.backend.entity.Trip;
import com.tripnest.backend.entity.User;
import com.tripnest.backend.repository.GroupMemberRepository;
import com.tripnest.backend.repository.GroupRepository;
import com.tripnest.backend.repository.TripRepository;
import com.tripnest.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
    }

    // Group banao
    public GroupResponse createGroup(GroupRequest request) {
    User user = getCurrentUser();

    // Debug — check karo user mil raha hai
    System.out.println("Creating group for user: " + user.getEmail() + " ID: " + user.getId());

    Group.GroupBuilder builder = Group.builder()
            .name(request.getName())
            .description(request.getDescription())
            .createdBy(user);  // ✅ User set ho raha hai

    if (request.getTripId() != null) {
        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() ->
                        new RuntimeException("Trip not found"));
        builder.trip(trip);
    }

    Group group = groupRepository.save(builder.build());

    GroupMember adminMember = GroupMember.builder()
            .group(group)
            .user(user)
            .role(GroupMember.MemberRole.ADMIN)
            .status(GroupMember.InviteStatus.ACCEPTED)
            .build();

    groupMemberRepository.save(adminMember);

    Group saved = groupRepository.findById(group.getId())
            .orElseThrow();
    return GroupResponse.fromEntity(saved);
}

    // Apne saare groups
    public List<GroupResponse> getMyGroups() {
        User user = getCurrentUser();

        // Jo groups tumne banaye
        List<Group> created = groupRepository
                .findByCreatedById(user.getId());

        // Jo groups mein member ho
        List<Long> memberGroupIds = groupMemberRepository
                .findByUserId(user.getId())
                .stream()
                .map(m -> m.getGroup().getId())
                .collect(Collectors.toList());

        // Dono merge karo — duplicates hatao
        List<Group> allGroups = created;
        for (Long gId : memberGroupIds) {
            boolean alreadyIn = allGroups.stream()
                    .anyMatch(g -> g.getId().equals(gId));
            if (!alreadyIn) {
                groupRepository.findById(gId)
                        .ifPresent(allGroups::add);
            }
        }

        return allGroups.stream()
                .map(GroupResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // Ek group detail
    public GroupResponse getGroupById(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() ->
                        new RuntimeException("Group not found"));
        return GroupResponse.fromEntity(group);
    }

    // Member invite karo — email se
    public GroupMemberResponse inviteMember(Long groupId,
                                             String email) {
        User currentUser = getCurrentUser();

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() ->
                        new RuntimeException("Group not found"));

        // Sirf admin invite kar sakta hai
        GroupMember currentMember = groupMemberRepository
                .findByGroupIdAndUserId(groupId, currentUser.getId())
                .orElseThrow(() ->
                        new RuntimeException("Access denied"));

        if (currentMember.getRole() != GroupMember.MemberRole.ADMIN) {
            throw new RuntimeException(
                    "Only admin can invite members");
        }

        // User exist karta hai?
        User invitedUser = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found: " + email));

        // Already member hai?
        if (groupMemberRepository.existsByGroupIdAndUserId(
                groupId, invitedUser.getId())) {
            throw new RuntimeException("Already a member!");
        }

        GroupMember member = GroupMember.builder()
                .group(group)
                .user(invitedUser)
                .role(GroupMember.MemberRole.MEMBER)
                .status(GroupMember.InviteStatus.PENDING)
                .build();

        return GroupMemberResponse.fromEntity(
                groupMemberRepository.save(member));
    }

    // Invitation accept karo
    public GroupMemberResponse acceptInvite(Long groupId) {
        User user = getCurrentUser();

        GroupMember member = groupMemberRepository
                .findByGroupIdAndUserId(groupId, user.getId())
                .orElseThrow(() ->
                        new RuntimeException("Invitation not found"));

        member.setStatus(GroupMember.InviteStatus.ACCEPTED);
        return GroupMemberResponse.fromEntity(
                groupMemberRepository.save(member));
    }

    // Invitation decline karo
    public GroupMemberResponse declineInvite(Long groupId) {
        User user = getCurrentUser();

        GroupMember member = groupMemberRepository
                .findByGroupIdAndUserId(groupId, user.getId())
                .orElseThrow(() ->
                        new RuntimeException("Invitation not found"));

        member.setStatus(GroupMember.InviteStatus.DECLINED);
        return GroupMemberResponse.fromEntity(
                groupMemberRepository.save(member));
    }

    // Member remove karo
    public void removeMember(Long groupId, Long memberId) {
        User currentUser = getCurrentUser();

        GroupMember currentMember = groupMemberRepository
                .findByGroupIdAndUserId(groupId, currentUser.getId())
                .orElseThrow(() ->
                        new RuntimeException("Access denied"));

        if (currentMember.getRole() != GroupMember.MemberRole.ADMIN) {
            throw new RuntimeException(
                    "Only admin can remove members");
        }

        GroupMember toRemove = groupMemberRepository
                .findById(memberId)
                .orElseThrow(() ->
                        new RuntimeException("Member not found"));

        groupMemberRepository.delete(toRemove);
    }

    // Group delete karo
    public void deleteGroup(Long groupId) {
        User user = getCurrentUser();
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() ->
                        new RuntimeException("Group not found"));

        if (!group.getCreatedBy().getId().equals(user.getId())) {
            throw new RuntimeException("Only creator can delete");
        }

        groupRepository.delete(group);
    }

    // Trip ke groups
    public List<GroupResponse> getGroupsByTrip(Long tripId) {
        return groupRepository.findByTripId(tripId)
                .stream()
                .map(GroupResponse::fromEntity)
                .collect(Collectors.toList());
    }
}