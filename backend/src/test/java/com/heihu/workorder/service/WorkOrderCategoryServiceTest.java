package com.heihu.workorder.service;

import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.WorkOrderCategory;
import com.heihu.workorder.exception.ApiException;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.WorkOrderCategoryRepository;
import com.heihu.workorder.repository.WorkOrderRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderCategoryServiceTest {

    @Mock
    private WorkOrderCategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkOrderRepository workOrderRepository;

    private WorkOrderCategoryService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderCategoryService();
        ReflectionTestUtils.setField(service, "categoryRepository", categoryRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "workOrderRepository", workOrderRepository);
    }

    @Test
    void deleteRejectsCategoryReferencedByWorkOrder() {
        WorkOrderCategory category = category();
        when(categoryRepository.findById(30L)).thenReturn(Optional.of(category));
        when(workOrderRepository.existsByFactoryIdAndCategoryId(1L, 30L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteCategory(1L, 30L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("工单分类已被工单引用，不能删除");

        verify(categoryRepository, never()).delete(category);
    }

    @Test
    void deleteRemovesUnreferencedCategory() {
        WorkOrderCategory category = category();
        when(categoryRepository.findById(30L)).thenReturn(Optional.of(category));

        service.deleteCategory(1L, 30L);

        verify(categoryRepository).delete(category);
    }

    @Test
    void deleteMissingCategoryReturnsNotFound() {
        when(categoryRepository.findById(30L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCategory(1L, 30L))
                .isInstanceOf(ApiException.class)
                .hasMessage("工单分类不存在");
    }

    private WorkOrderCategory category() {
        Factory factory = new Factory();
        factory.setId(1L);
        factory.setCode("HHGD");
        factory.setName("Test Factory");
        factory.setCreatedAt(LocalDateTime.now());

        WorkOrderCategory category = new WorkOrderCategory();
        category.setId(30L);
        category.setFactory(factory);
        category.setName("Default");
        category.setCreatedAt(LocalDateTime.now());
        return category;
    }
}
