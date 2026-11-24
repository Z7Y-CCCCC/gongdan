package com.heihu.workorder.service;

import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.WorkOrder;
import com.heihu.workorder.model.Product;
import com.heihu.workorder.dto.WorkOrderRequest;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(workOrderRepository.findByIdAndFactoryIdForUpdate(100L, 1L)).thenReturn(Optional.of(workOrder));
        when(reportRepository.existsByFactoryIdAndWorkOrderId(1L, 100L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工单已有报工记录，不能删除");

        verify(workOrderRepository, never()).delete(workOrder);
    }

    @Test
    void deleteRemovesWorkOrderWithoutReports() {
        WorkOrder workOrder = workOrder();
        when(workOrderRepository.findByIdAndFactoryIdForUpdate(100L, 1L)).thenReturn(Optional.of(workOrder));

        service.delete(1L, 100L);

        verify(workOrderRepository).delete(workOrder);
    }

    @Test
    void staleVersionIsRejectedBeforeModifyingTheManagedEntity() {
        WorkOrder order = workOrder();
        order.setVersion(7L);
        when(workOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        WorkOrderRequest request = validRequest();
        request.setVersion(6L);

        assertThatThrownBy(() -> service.update(1L, 100L, request))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        assertThat(order.getVersion()).isEqualTo(7L);
        verifyNoInteractions(productRepository);
    }

    @Test
    void invalidDatesAreRejectedInsteadOfBeingStoredAsNull() {
        stubProduct();
        WorkOrderRequest request = validRequest();
        request.setPlanStart("not-a-date");
        assertThatThrownBy(() -> service.create(1L, null, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("日期时间");
        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void reversedPlanRangeIsRejected() {
        stubProduct();
        WorkOrderRequest request = validRequest();
        request.setPlanStart("2026-09-11 12:00:00");
        request.setPlanEnd("2026-09-11 11:00:00");
        assertThatThrownBy(() -> service.create(1L, null, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("结束");
    }

    @Test
    void foreignOrMissingCategoryIsNotSilentlyIgnored() {
        stubProduct();
        WorkOrderRequest request = validRequest();
        request.setCategoryId(9L);
        assertThatThrownBy(() -> service.create(1L, null, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("分类");
    }

    @Test
    void nullTasksAndDuplicateSequencesAreRejected() {
        stubProduct();
        WorkOrderRequest request = validRequest();
        request.setTasks(java.util.Collections.singletonList(null));
        assertThatThrownBy(() -> service.create(1L, null, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("工序");
        WorkOrderRequest.TaskItem first = new WorkOrderRequest.TaskItem();
        first.setSequenceNo(1);
        first.setProcessName("裁切");
        request.setTasks(java.util.Arrays.asList(first, first));
        assertThatThrownBy(() -> service.create(1L, null, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("工序");
    }

    private WorkOrderRequest validRequest() {
        WorkOrderRequest request = new WorkOrderRequest();
        request.setCode("GD002");
        request.setProductId(10L);
        request.setPlannedQuantity(10);
        request.setStatus("NOT_STARTED");
        return request;
    }

    @Test
    void extremeOverproductionCannotReturnANegativeProgressPercent() {
        WorkOrder order = workOrder();
        order.setTaskDataJson("[{\"sequenceNo\":1,\"processName\":\"Cut\",\"plannedQuantity\":1,\"completedQuantity\":2147483647}]");
        when(workOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        assertThat(service.get(1L, 100L).getProgressPercent()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void responseKeepsActualUnitAndUsesNullForMissingLegacyProductOrUnit() {
        WorkOrder order = workOrder();
        when(workOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        assertThat(service.get(1L, 100L).getUnitName()).isNull();
        Product product = new Product();
        order.setProduct(product);
        assertThat(service.get(1L, 100L).getUnitName()).isNull();
        com.heihu.workorder.model.Unit unit = new com.heihu.workorder.model.Unit();
        unit.setName("kg");
        product.setUnit(unit);
        assertThat(service.get(1L, 100L).getUnitName()).isEqualTo("kg");
    }

    private void stubProduct() {
        Product product = new Product();
        product.setId(10L);
        product.setFactory(workOrder().getFactory());
        product.setCode("P001");
        product.setName("Product");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
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
