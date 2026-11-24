package com.heihu.workorder.repository;

import com.heihu.workorder.model.WorkOrderReportImage;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface WorkOrderReportImageRepository extends JpaRepository<WorkOrderReportImage, Long> {

    List<WorkOrderReportImage> findByReportIdOrderByCreatedAtAsc(Long reportId);

    List<WorkOrderReportImage> findByFactoryIdAndReportIdInOrderByCreatedAtAscIdAsc(Long factoryId, Collection<Long> reportIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<WorkOrderReportImage> findByFactoryIdAndIdIn(Long factoryId, Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<WorkOrderReportImage> findByReportIsNullAndCreatedAtBefore(LocalDateTime createdBefore);
}
