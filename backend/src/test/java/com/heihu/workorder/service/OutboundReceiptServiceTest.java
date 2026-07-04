package com.heihu.workorder.service;

import com.heihu.workorder.dto.OutboundReceiptRequest;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.OutboundReceipt;
import com.heihu.workorder.model.Product;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.OutboundReceiptRepository;
import com.heihu.workorder.repository.ProductRepository;
import com.heihu.workorder.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboundReceiptServiceTest {

    @Mock
    private OutboundReceiptRepository outboundReceiptRepository;

    @Mock
    private FactoryRepository factoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    private OutboundReceiptService service;

    @BeforeEach
    void setUp() {
        service = new OutboundReceiptService(outboundReceiptRepository, factoryRepository, productRepository, userRepository);
    }

    @Test
    void createUsesLockedProductForStockUpdate() {
        OutboundReceiptRequest request = new OutboundReceiptRequest();
        request.setCode("CK001");
        request.setOutboundType("销售出库");
        request.setOutboundTime("2026-07-04 14:00:00");
        OutboundReceiptRequest.OutboundReceiptItemRequest item = new OutboundReceiptRequest.OutboundReceiptItemRequest();
        item.setProductId(10L);
        item.setQuantity(3);
        request.setItems(Collections.singletonList(item));

        Product product = product(10L, 5);
        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory(1L)));
        when(outboundReceiptRepository.findByFactoryIdAndCode(1L, "CK001")).thenReturn(Optional.empty());
        when(productRepository.findByIdAndFactoryIdForUpdate(10L, 1L)).thenReturn(Optional.of(product));
        when(outboundReceiptRepository.save(any(OutboundReceipt.class))).thenAnswer(invocation -> {
            OutboundReceipt receipt = invocation.getArgument(0);
            receipt.setId(99L);
            return receipt;
        });

        service.create(1L, 2L, request);

        assertThat(product.getInventoryQty()).isEqualTo(2);
        verify(productRepository).findByIdAndFactoryIdForUpdate(10L, 1L);
        verify(productRepository, never()).findById(10L);
        verify(productRepository).save(product);
    }

    private Factory factory(Long id) {
        Factory factory = new Factory();
        factory.setId(id);
        factory.setCode("FAC" + id);
        factory.setName("Factory " + id);
        factory.setEnabled(true);
        factory.setCreatedAt(LocalDateTime.now());
        return factory;
    }

    private Product product(Long id, int inventoryQty) {
        Product product = new Product();
        product.setId(id);
        product.setFactory(factory(1L));
        product.setCode("P" + id);
        product.setName("Product " + id);
        product.setAttribute("成品");
        product.setInventoryQty(inventoryQty);
        product.setCreatedAt(LocalDateTime.now());
        return product;
    }
}
