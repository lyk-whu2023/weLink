package com.epsilon.welink.relation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.epsilon.welink.common.constant.RedisConstants;
import com.epsilon.welink.common.exception.BusinessException;
import com.epsilon.welink.common.result.ResultCode;
import com.epsilon.welink.message.service.MessageService;
import com.epsilon.welink.relation.dto.CreateGroupRequest;
import com.epsilon.welink.relation.entity.FriendRelation;
import com.epsilon.welink.relation.entity.GroupInfo;
import com.epsilon.welink.relation.entity.GroupMember;
import com.epsilon.welink.relation.mapper.FriendRelationMapper;
import com.epsilon.welink.relation.mapper.GroupInfoMapper;
import com.epsilon.welink.relation.mapper.GroupMemberMapper;
import com.epsilon.welink.user.entity.User;
import com.epsilon.welink.user.service.UserService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RelationService {

    private final FriendRelationMapper friendRelationMapper;
    private final GroupInfoMapper groupInfoMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final UserService userService;
    private final MessageService messageService;
    private final RedisTemplate<String, Object> redisTemplate;

    public RelationService(FriendRelationMapper friendRelationMapper,
                           GroupInfoMapper groupInfoMapper,
                           GroupMemberMapper groupMemberMapper,
                           UserService userService,
                           MessageService messageService,
                           RedisTemplate<String, Object> redisTemplate) {
        this.friendRelationMapper = friendRelationMapper;
        this.groupInfoMapper = groupInfoMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.userService = userService;
        this.messageService = messageService;
        this.redisTemplate = redisTemplate;
    }

    public void sendFriendRequest(Long userId, Long friendId) {
        if (userId.equals(friendId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能添加自己为好友");
        }

        LambdaQueryWrapper<FriendRelation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FriendRelation::getUserId, userId)
                .eq(FriendRelation::getFriendId, friendId);
        if (friendRelationMapper.selectCount(queryWrapper) > 0) {
            throw new BusinessException(ResultCode.FRIEND_ALREADY_EXISTS);
        }

        FriendRelation relation = new FriendRelation();
        relation.setUserId(userId);
        relation.setFriendId(friendId);
        relation.setStatus(0);
        friendRelationMapper.insert(relation);
    }

    public void acceptFriendRequest(Long userId, Long friendId) {
        LambdaQueryWrapper<FriendRelation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FriendRelation::getUserId, friendId)
                .eq(FriendRelation::getFriendId, userId)
                .eq(FriendRelation::getStatus, 0);
        FriendRelation relation = friendRelationMapper.selectOne(queryWrapper);

        if (relation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "好友申请不存在");
        }

        relation.setStatus(1);
        friendRelationMapper.updateById(relation);

        FriendRelation reverseRelation = new FriendRelation();
        reverseRelation.setUserId(userId);
        reverseRelation.setFriendId(friendId);
        reverseRelation.setStatus(1);
        friendRelationMapper.insert(reverseRelation);
    }

    public void rejectFriendRequest(Long userId, Long friendId) {
        LambdaQueryWrapper<FriendRelation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FriendRelation::getUserId, friendId)
                .eq(FriendRelation::getFriendId, userId)
                .eq(FriendRelation::getStatus, 0);
        FriendRelation relation = friendRelationMapper.selectOne(queryWrapper);

        if (relation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "好友申请不存在");
        }

        relation.setStatus(2);
        friendRelationMapper.updateById(relation);
    }

    public List<User> getFriendList(Long userId) {
        LambdaQueryWrapper<FriendRelation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FriendRelation::getUserId, userId)
                .eq(FriendRelation::getStatus, 1);
        List<FriendRelation> relations = friendRelationMapper.selectList(queryWrapper);

        return relations.stream()
                .map(r -> {
                    User friend = userService.getUserInfo(r.getFriendId());
                    if (friend != null) {
                        Object onlineStatus = redisTemplate.opsForValue().get(RedisConstants.USER_ONLINE_PREFIX + r.getFriendId());
                        friend.setOnline(onlineStatus != null);
                    }
                    return friend;
                })
                .toList();
    }

    public List<Long> getFriendIds(Long userId) {
        LambdaQueryWrapper<FriendRelation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FriendRelation::getUserId, userId)
                .eq(FriendRelation::getStatus, 1);
        return friendRelationMapper.selectList(queryWrapper).stream()
                .map(FriendRelation::getFriendId)
                .toList();
    }

    public List<User> getPendingFriendRequests(Long userId) {
        LambdaQueryWrapper<FriendRelation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FriendRelation::getFriendId, userId)
                .eq(FriendRelation::getStatus, 0);
        List<FriendRelation> relations = friendRelationMapper.selectList(queryWrapper);

        return relations.stream()
                .map(r -> userService.getUserInfo(r.getUserId()))
                .toList();
    }

    public void sendFriendRequestByUsername(Long userId, String username) {
        User targetUser = userService.getUserByUsername(username);
        if (targetUser == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }
        sendFriendRequest(userId, targetUser.getId());
    }

    public void deleteFriend(Long userId, Long friendId) {
        LambdaQueryWrapper<FriendRelation> queryWrapper1 = new LambdaQueryWrapper<>();
        queryWrapper1.eq(FriendRelation::getUserId, userId)
                .eq(FriendRelation::getFriendId, friendId);
        friendRelationMapper.delete(queryWrapper1);

        LambdaQueryWrapper<FriendRelation> queryWrapper2 = new LambdaQueryWrapper<>();
        queryWrapper2.eq(FriendRelation::getUserId, friendId)
                .eq(FriendRelation::getFriendId, userId);
        friendRelationMapper.delete(queryWrapper2);
    }

    @Transactional
    public GroupInfo createGroup(Long userId, CreateGroupRequest request) {
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setGroupName(request.getGroupName());
        groupInfo.setOwnerId(userId);
        groupInfo.setMemberCount(1);
        groupInfo.setStatus(1);
        groupInfoMapper.insert(groupInfo);

        GroupMember owner = new GroupMember();
        owner.setGroupId(groupInfo.getId());
        owner.setUserId(userId);
        owner.setRole(2);
        owner.setLastReadSeq(0L);
        groupMemberMapper.insert(owner);

        if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
            for (Long memberId : request.getMemberIds()) {
                if (!memberId.equals(userId)) {
                    GroupMember member = new GroupMember();
                    member.setGroupId(groupInfo.getId());
                    member.setUserId(memberId);
                    member.setRole(0);
                    member.setLastReadSeq(0L);
                    groupMemberMapper.insert(member);
                }
            }
            groupInfo.setMemberCount(groupInfo.getMemberCount() + request.getMemberIds().size());
            groupInfoMapper.updateById(groupInfo);
        }

        return groupInfo;
    }

    public List<GroupInfo> getGroupList(Long userId) {
        LambdaQueryWrapper<GroupMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GroupMember::getUserId, userId);
        List<GroupMember> memberships = groupMemberMapper.selectList(queryWrapper);

        return memberships.stream()
                .map(m -> groupInfoMapper.selectById(m.getGroupId()))
                .toList();
    }

    public void joinGroup(Long userId, Long groupId) {
        GroupInfo groupInfo = groupInfoMapper.selectById(groupId);
        if (groupInfo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "群组不存在");
        }

        LambdaQueryWrapper<GroupMember> existQuery = new LambdaQueryWrapper<>();
        existQuery.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId);
        if (groupMemberMapper.selectCount(existQuery) > 0) {
            throw new BusinessException(ResultCode.GROUP_NO_PERMISSION, "已经是群成员");
        }

        GroupMember newMember = new GroupMember();
        newMember.setGroupId(groupId);
        newMember.setUserId(userId);
        newMember.setRole(0);
        newMember.setLastReadSeq(messageService.getCurrentGroupSeq(groupId));
        groupMemberMapper.insert(newMember);

        groupInfo.setMemberCount(groupMemberMapper.selectCount(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId)
        ).intValue());
        groupInfoMapper.updateById(groupInfo);
    }

    public void joinGroupByName(Long userId, String groupName) {
        LambdaQueryWrapper<GroupInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GroupInfo::getGroupName, groupName);
        GroupInfo groupInfo = groupInfoMapper.selectOne(queryWrapper);

        if (groupInfo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "群组不存在");
        }

        joinGroup(userId, groupInfo.getId());
    }

    public List<GroupMember> getGroupMembers(Long groupId) {
        LambdaQueryWrapper<GroupMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GroupMember::getGroupId, groupId);
        return groupMemberMapper.selectList(queryWrapper);
    }

    public void inviteMembers(Long userId, Long groupId, List<Long> memberIds) {
        GroupMember member = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getUserId, userId)
                        .in(GroupMember::getRole, 1, 2)
        );

        if (member == null) {
            throw new BusinessException(ResultCode.GROUP_NO_PERMISSION);
        }

        for (Long memberId : memberIds) {
            LambdaQueryWrapper<GroupMember> existQuery = new LambdaQueryWrapper<>();
            existQuery.eq(GroupMember::getGroupId, groupId)
                    .eq(GroupMember::getUserId, memberId);
            if (groupMemberMapper.selectCount(existQuery) == 0) {
                GroupMember newMember = new GroupMember();
                newMember.setGroupId(groupId);
                newMember.setUserId(memberId);
                newMember.setRole(0);
                newMember.setLastReadSeq(messageService.getCurrentGroupSeq(groupId));
                groupMemberMapper.insert(newMember);
            }
        }

        GroupInfo groupInfo = groupInfoMapper.selectById(groupId);
        groupInfo.setMemberCount(groupMemberMapper.selectCount(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId)
        ).intValue());
        groupInfoMapper.updateById(groupInfo);
    }

    public void kickMember(Long userId, Long groupId, Long targetId) {
        GroupMember operator = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getUserId, userId)
                        .in(GroupMember::getRole, 1, 2)
        );

        if (operator == null) {
            throw new BusinessException(ResultCode.GROUP_NO_PERMISSION);
        }

        GroupMember target = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getUserId, targetId)
        );

        if (target == null) {
            throw new BusinessException(ResultCode.GROUP_NOT_MEMBER);
        }

        if (target.getRole() == 2) {
            throw new BusinessException(ResultCode.GROUP_NO_PERMISSION, "不能踢出群主");
        }

        groupMemberMapper.deleteById(target.getId());

        GroupInfo groupInfo = groupInfoMapper.selectById(groupId);
        groupInfo.setMemberCount(groupMemberMapper.selectCount(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId)
        ).intValue());
        groupInfoMapper.updateById(groupInfo);
    }

    public void quitGroup(Long userId, Long groupId) {
        GroupMember member = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getUserId, userId)
        );

        if (member == null) {
            throw new BusinessException(ResultCode.GROUP_NOT_MEMBER);
        }

        if (member.getRole() == 2) {
            throw new BusinessException(ResultCode.GROUP_NO_PERMISSION, "群主不能退出群组，请先转移群主权限");
        }

        groupMemberMapper.deleteById(member.getId());

        GroupInfo groupInfo = groupInfoMapper.selectById(groupId);
        groupInfo.setMemberCount(groupMemberMapper.selectCount(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId)
        ).intValue());
        groupInfoMapper.updateById(groupInfo);
    }
}
