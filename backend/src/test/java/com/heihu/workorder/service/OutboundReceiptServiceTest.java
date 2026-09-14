package com.heihu.workorder.service;

import com.heihu.workorder.dto.OutboundReceiptRequest;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.OutboundReceipt;
import com.heihu.workorder.model.OutboundReceiptItem;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
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

    @Test
    void stockLocksAreAcquiredInStableProductIdOrder() {
        OutboundReceiptRequest request = request(20L, 1);
        OutboundReceiptRequest.OutboundReceiptItemRequest second = new OutboundReceiptRequest.OutboundReceiptItemRequest();
        second.setProductId(10L);
        second.setQuantity(1);
        request.setItems(java.util.Arrays.asList(request.getItems().get(0), second));
        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory(1L)));
        when(productRepository.findByIdAndFactoryIdForUpdate(10L, 1L)).thenReturn(Optional.of(product(10L, 2)));
        when(productRepository.findByIdAndFactoryIdForUpdate(20L, 1L)).thenReturn(Optional.of(product(20L, 2)));
        when(outboundReceiptRepository.save(any(OutboundReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.create(1L, null, request);
        org.mockito.InOrder order = inOrder(productRepository);
        order.verify(productRepository).findByIdAndFactoryIdForUpdate(10L, 1L);
        order.verify(productRepository).findByIdAndFactoryIdForUpdate(20L, 1L);
    }

    @Test
    void deleteLocksReceiptAndRejectsStockOverflow() {
        OutboundReceipt receipt = new OutboundReceipt();
        receipt.setId(99L);
        receipt.setFactory(factory(1L));
        Product product = product(10L, Integer.MAX_VALUE);
        OutboundReceiptItem item = new OutboundReceiptItem();
        item.setProduct(product);
        item.setQuantity(1);
        receipt.getItems().add(item);
        when(outboundReceiptRepository.findByIdAndFactoryIdForUpdate(99L, 1L)).thenReturn(Optional.of(receipt));
        when(productRepository.findByIdAndFactoryIdForUpdate(10L, 1L)).thenReturn(Optional.of(product));
        assertThatThrownBy(() -> service.delete(1L, 99L)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("库存数量");
        verify(outboundReceiptRepository, never()).findById(99L);
        verify(outboundReceiptRepository, never()).delete(receipt);
    }

    private OutboundReceiptRequest request(Long productId, int quantity) {
        OutboundReceiptRequest request = new OutboundReceiptRequest();
        request.setCode("CK001");
        request.setOutboundType("销售出库");
        request.setOutboundTime("2026-09-11 12:00:00");
        OutboundReceiptRequest.OutboundReceiptItemRequest item = new OutboundReceiptRequest.OutboundReceiptItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        request.setItems(Collections.singletonList(item));
        return request;
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
