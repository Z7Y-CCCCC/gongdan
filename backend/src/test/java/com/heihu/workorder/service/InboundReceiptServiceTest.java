package com.heihu.workorder.service;

import com.heihu.workorder.dto.InboundReceiptRequest;
import com.heihu.workorder.model.Factory;
import com.heihu.workorder.model.InboundReceipt;
import com.heihu.workorder.model.InboundReceiptItem;
import com.heihu.workorder.model.Product;
import com.heihu.workorder.repository.FactoryRepository;
import com.heihu.workorder.repository.InboundReceiptRepository;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundReceiptServiceTest {

    @Mock
    private InboundReceiptRepository inboundReceiptRepository;

    @Mock
    private FactoryRepository factoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    private InboundReceiptService service;

    @BeforeEach
    void setUp() {
        service = new InboundReceiptService(inboundReceiptRepository, factoryRepository, productRepository, userRepository);
    }

    @Test
    void createRejectsEmptyItems() {
        InboundReceiptRequest request = new InboundReceiptRequest();
        request.setCode("RK001");
        request.setInboundType("采购入库");
        request.setInboundTime("2026-07-04 14:00:00");
        request.setItems(Collections.emptyList());

        when(factoryRepository.findById(1L)).thenReturn(Optional.of(factory(1L)));
        when(inboundReceiptRepository.findByFactoryIdAndCode(1L, "RK001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, 2L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("入库明细");

        verify(inboundReceiptRepository, never()).save(org.mockito.ArgumentMatchers.any(InboundReceipt.class));
        verifyNoInteractions(productRepository);
    }

    @Test
    void deleteRejectsWhenInboundStockHasBeenConsumed() {
        InboundReceipt receipt = new InboundReceipt();
        receipt.setId(99L);
        receipt.setFactory(factory(1L));

        InboundReceiptItem item = new InboundReceiptItem();
        item.setInboundReceipt(receipt);
        item.setProduct(product(10L, 10));
        item.setQuantity(8);
        receipt.getItems().add(item);

        Product currentProduct = product(10L, 2);
        when(inboundReceiptRepository.findById(99L)).thenReturn(Optional.of(receipt));
        when(productRepository.findByIdAndFactoryIdForUpdate(10L, 1L)).thenReturn(Optional.of(currentProduct));

        assertThatThrownBy(() -> service.delete(1L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("库存");

        verify(productRepository, never()).save(currentProduct);
        verify(inboundReceiptRepository, never()).delete(receipt);
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
