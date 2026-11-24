package com.heihu.workorder.service;

import com.heihu.workorder.dto.InternalChatContactResponse;
import com.heihu.workorder.dto.InternalChatDeviceResponse;
import com.heihu.workorder.dto.InternalChatMessageResponse;
import com.heihu.workorder.dto.SendInternalChatMessageRequest;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.InternalMessage;
import com.heihu.workorder.model.UserSession;
import com.heihu.workorder.repository.InternalMessageRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.UserSessionRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InternalChatService {

    private final UserRepository userRepository;
    private final InternalMessageRepository messageRepository;
    private final UserSessionRepository sessionRepository;

    public InternalChatService(
            UserRepository userRepository,
            InternalMessageRepository messageRepository,
            UserSessionRepository sessionRepository
    ) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public List<InternalChatContactResponse> listContacts(AuthenticatedUser currentUser) {
        List<FactoryUser> users = userRepository.findByFactoryIdAndEnabledTrue(currentUser.getFactoryId())
                .stream()
                .filter(user -> !user.getId().equals(currentUser.getUserId()))
                .collect(Collectors.toList());
        List<InternalMessage> recentMessages = messageRepository
                .findLatestConversations(currentUser.getFactoryId(), currentUser.getUserId());
        Map<Long, InternalMessage> latestByPeer = new HashMap<>();
        Map<Long, Integer> unreadByPeer = new HashMap<>();
        for (InternalMessage message : recentMessages) {
            boolean sentByCurrent = message.getSender().getId().equals(currentUser.getUserId());
            Long peerId = sentByCurrent ? message.getReceiver().getId() : message.getSender().getId();
            latestByPeer.putIfAbsent(peerId, message);
        }
        for (Object[] count : messageRepository.countUnreadBySender(currentUser.getFactoryId(), currentUser.getUserId())) {
            unreadByPeer.put(((Number) count[0]).longValue(), (int) Math.min(Integer.MAX_VALUE, ((Number) count[1]).longValue()));
        }
        List<InternalChatContactResponse> result = new ArrayList<>();
        Instant now = Instant.now();
        LocalDateTime nowLocal = LocalDateTime.now();
        for (FactoryUser user : users) {
            InternalMessage latest = latestByPeer.get(user.getId());
            List<UserSession> sessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
            List<InternalChatDeviceResponse> onlineDevices = sessions.stream()
                    .filter(session -> isOnline(session, now, nowLocal))
                    .map(InternalChatDeviceResponse::new)
                    .collect(Collectors.toList());
            LocalDateTime lastSeenAt = sessions.stream()
                    .map(session -> session.getLastActiveAt() == null ? session.getCreatedAt() : session.getLastActiveAt())
                    .filter(value -> value != null)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            LocalDateTime lastLoginAt = sessions.stream()
                    .map(UserSession::getCreatedAt)
                    .filter(value -> value != null)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            result.add(new InternalChatContactResponse(
                    user,
                    latest == null ? null : latest.getContent(),
                    latest == null ? null : latest.getCreatedAt(),
                    unreadByPeer.getOrDefault(user.getId(), 0),
                    !onlineDevices.isEmpty(),
                    onlineDevices,
                    lastSeenAt,
                    lastLoginAt
            ));
        }
        result.sort(Comparator
                .comparing(InternalChatContactResponse::getLastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(InternalChatContactResponse::getName));
        return result;
    }

    private boolean isOnline(UserSession session, Instant now, LocalDateTime nowLocal) {
        if (session.isKicked() || session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now)) {
            return false;
        }
        LocalDateTime lastActiveAt = session.getLastActiveAt() == null ? session.getCreatedAt() : session.getLastActiveAt();
        return lastActiveAt != null && !lastActiveAt.isBefore(nowLocal.minusSeconds(90));
    }

    @Transactional(readOnly = true)
    public List<InternalChatMessageResponse> listMessages(AuthenticatedUser currentUser, Long peerId) {
        FactoryUser peer = getEnabledPeer(currentUser, peerId);
        return messageRepository.findConversation(currentUser.getFactoryId(), currentUser.getUserId(), peer.getId())
                .stream()
                .map(InternalChatMessageResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public InternalChatMessageResponse sendMessage(
            AuthenticatedUser currentUser,
            SendInternalChatMessageRequest request
    ) {
        FactoryUser sender = userRepository.findByIdAndFactoryId(currentUser.getUserId(), currentUser.getFactoryId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "当前用户不存在"));
        FactoryUser receiver = getEnabledPeer(currentUser, request.getReceiverId());
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "消息内容不能为空");
        }
        InternalMessage message = new InternalMessage();
        message.setFactory(sender.getFactory());
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        return InternalChatMessageResponse.from(messageRepository.save(message));
    }

    @Transactional
    public void markRead(AuthenticatedUser currentUser, Long peerId) {
        FactoryUser peer = getEnabledPeer(currentUser, peerId);
        LocalDateTime now = LocalDateTime.now();
        List<InternalMessage> unread = messageRepository.findUnreadConversation(
                currentUser.getFactoryId(), currentUser.getUserId(), peer.getId());
        unread.forEach(message -> message.setReadAt(now));
        if (!unread.isEmpty()) {
            messageRepository.saveAll(unread);
        }
    }

    private FactoryUser getEnabledPeer(AuthenticatedUser currentUser, Long peerId) {
        if (peerId == null || peerId.equals(currentUser.getUserId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "接收人无效");
        }
        FactoryUser peer = userRepository.findByIdAndFactoryId(peerId, currentUser.getFactoryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "联系人不存在"));
        if (!peer.isEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "该联系人已停用");
        }
        return peer;
    }
}
