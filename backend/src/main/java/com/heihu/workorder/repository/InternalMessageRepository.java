package com.heihu.workorder.repository;

import com.heihu.workorder.model.InternalMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InternalMessageRepository extends JpaRepository<InternalMessage, Long> {

    List<InternalMessage> findTop5000ByFactoryIdOrderByCreatedAtDesc(Long factoryId);

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
