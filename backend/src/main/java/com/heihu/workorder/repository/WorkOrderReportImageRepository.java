package com.heihu.workorder.repository;

import com.heihu.workorder.model.WorkOrderReportImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderReportImageRepository extends JpaRepository<WorkOrderReportImage, Long> {

    List<WorkOrderReportImage> findByReportIdOrderByCreatedAtAsc(Long reportId);

    List<WorkOrderReportImage> findByFactoryIdAndIdIn(Long factoryId, Collection<Long> ids);
}
