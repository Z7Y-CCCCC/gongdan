package com.heihu.workorder.service;

import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.WorkOrder;
import com.heihu.workorder.repository.ProductRepository;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.WorkOrderCategoryRepository;
import com.heihu.workorder.repository.WorkOrderReportRepository;
import com.heihu.workorder.repository.WorkOrderRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkOrderCategoryRepository categoryRepository;

    @Mock
    private WorkOrderReportRepository reportRepository;

    private WorkOrderService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderService(
                workOrderRepository,
                productRepository,
                userRepository,
                categoryRepository,
                reportRepository
        );
    }

    @Test
    void deleteRejectsWorkOrderWithReports() {
        WorkOrder workOrder = workOrder();
        when(workOrderRepository.findById(100L)).thenReturn(Optional.of(workOrder));
        when(reportRepository.existsByFactoryIdAndWorkOrderId(1L, 100L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工单已有报工记录，不能删除");

        verify(workOrderRepository, never()).delete(workOrder);
    }

    @Test
    void deleteRemovesWorkOrderWithoutReports() {
        WorkOrder workOrder = workOrder();
        when(workOrderRepository.findById(100L)).thenReturn(Optional.of(workOrder));

        service.delete(1L, 100L);

        verify(workOrderRepository).delete(workOrder);
    }

    private WorkOrder workOrder() {
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode("HHGD");
        factory.setName("Test Factory");
        factory.setCreatedAt(LocalDateTime.now());

        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(100L);
        workOrder.setFactory(factory);
        workOrder.setCode("GD001");
        workOrder.setStatus("NOT_STARTED");
        workOrder.setPriority("NORMAL");
        workOrder.setPlannedQuantity(10);
        workOrder.setGoodQuantity(0);
        workOrder.setDefectQuantity(0);
        workOrder.setCreatedAt(LocalDateTime.now());
        workOrder.setUpdatedAt(LocalDateTime.now());
        return workOrder;
    }
}
