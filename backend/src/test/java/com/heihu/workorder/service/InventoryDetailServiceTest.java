package com.heihu.workorder.service;

import com.heihu.workorder.dto.InventoryDetailResponse;
import com.heihu.workorder.repository.InboundReceiptRepository;
import com.heihu.workorder.repository.OutboundReceiptRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryDetailServiceTest {

    @Mock
    private InboundReceiptRepository inboundReceiptRepository;

    @Mock
    private OutboundReceiptRepository outboundReceiptRepository;

    private InventoryDetailService service;

    @BeforeEach
    void setUp() {
        service = new InventoryDetailService(inboundReceiptRepository, outboundReceiptRepository);
        when(inboundReceiptRepository.findByFactoryId(eq(1L), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(outboundReceiptRepository.findByFactoryId(eq(1L), any(Pageable.class)))
                .thenReturn(Page.empty());
    }

    @Test
    void listReturnsRedisSerializableArrayListInsteadOfSubListView() {
        List<InventoryDetailResponse> result = service.list(
                1L, null, null, null, null, null, null, null, null,
                null, null, 1, 20);

        assertThat(result).isInstanceOf(ArrayList.class).isEmpty();
    }
}
