package com.heihu.workorder.repository;

import com.heihu.workorder.model.InternalMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InternalMessageRepository extends JpaRepository<InternalMessage, Long> {

    List<InternalMessage> findTop5000ByFactoryIdOrderByCreatedAtDesc(Long factoryId);

    @Query("select m from InternalMessage m where m.factory.id = :factoryId "
            + "and (m.sender.id = :userId or m.receiver.id = :userId) "
            + "and not exists (select newer.id from InternalMessage newer "
            + "where newer.factory.id = :factoryId "
            + "and ((newer.sender.id = m.sender.id and newer.receiver.id = m.receiver.id) "
            + "or (newer.sender.id = m.receiver.id and newer.receiver.id = m.sender.id)) "
            + "and (newer.createdAt > m.createdAt or (newer.createdAt = m.createdAt and newer.id > m.id))) "
            + "order by m.createdAt desc, m.id desc")
    List<InternalMessage> findLatestConversations(@Param("factoryId") Long factoryId, @Param("userId") Long userId);

    @Query("select m.sender.id, count(m) from InternalMessage m "
            + "where m.factory.id = :factoryId and m.receiver.id = :userId and m.readAt is null group by m.sender.id")
    List<Object[]> countUnreadBySender(@Param("factoryId") Long factoryId, @Param("userId") Long userId);

    @Query("select m from InternalMessage m "
            + "where m.factory.id = :factoryId "
            + "and ((m.sender.id = :userId and m.receiver.id = :peerId) "
            + "or (m.sender.id = :peerId and m.receiver.id = :userId)) "
            + "order by m.createdAt asc")
    List<InternalMessage> findConversation(
            @Param("factoryId") Long factoryId,
            @Param("userId") Long userId,
            @Param("peerId") Long peerId
    );

    @Query("select m from InternalMessage m "
            + "where m.factory.id = :factoryId "
            + "and m.receiver.id = :userId and m.sender.id = :peerId "
            + "and m.readAt is null")
    List<InternalMessage> findUnreadConversation(
            @Param("factoryId") Long factoryId,
            @Param("userId") Long userId,
            @Param("peerId") Long peerId
    );
}
