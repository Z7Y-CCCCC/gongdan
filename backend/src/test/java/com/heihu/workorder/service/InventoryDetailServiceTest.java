package com.heihu.workorder.service;

import com.heihu.workorder.dto.InventoryDetailPageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryDetailServiceTest {

    @Test
    void unknownSourceDoesNotQueryTheDatabase() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        assertThat(list(new InventoryDetailService(jdbc), "unknown", 1).getTotal()).isZero();
        verifyNoInteractions(jdbc);
    }

    @Test
    void outOfRangePageOnlyCountsMatchingReceiptLines() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(contains("COUNT(*)"), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(3L);
        InventoryDetailPageResponse result = list(new InventoryDetailService(jdbc), "in", Integer.MAX_VALUE);
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(3L);
        verify(jdbc).queryForObject(contains("FROM inbound_receipts"), any(MapSqlParameterSource.class), eq(Long.class));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void cacheKeysCannotCollideWhenFilterValuesContainSeparators() {
        assertThat(InventoryDetailService.cacheKey(new Object[]{1L, "a:b", "c"}))
                .isNotEqualTo(InventoryDetailService.cacheKey(new Object[]{1L, "a", "b:c"}));
        assertThat(InventoryDetailService.cacheKey(new Object[]{1L, "null"}))
                .isNotEqualTo(InventoryDetailService.cacheKey(new Object[]{1L, null}));
        assertThat(InventoryDetailService.cacheKey(new Object[]{1L, " In "}))
                .isEqualTo(InventoryDetailService.cacheKey(new Object[]{1L, "in"}));
    }

    private InventoryDetailPageResponse list(InventoryDetailService service, String source, int page) {
        return service.list(1L, null, null, source, null, null, null, null, null, null, null, page, 20);
    }
}
