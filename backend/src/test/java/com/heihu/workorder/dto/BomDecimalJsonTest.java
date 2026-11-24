package com.heihu.workorder.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BomDecimalJsonTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void responseDecimalsAreStringsAndPreserveTheDecimal18Scale4UpperBound() throws Exception {
        BomResponse response = new BomResponse();
        response.setOutputQuantity(new BigDecimal("99999999999999.9999"));
        BomItemResponse item = new BomItemResponse();
        item.setQuantity(new BigDecimal("99999999999999.9998"));
        item.setScrapRate(new BigDecimal("0.1234"));
        response.setItems(Collections.singletonList(item));
        JsonNode json = mapper.readTree(mapper.writeValueAsString(response));
        assertTrue(json.get("outputQuantity").isTextual());
        assertEquals("99999999999999.9999", json.get("outputQuantity").asText());
        assertTrue(json.get("items").get(0).get("quantity").isTextual());
        assertEquals("99999999999999.9998", json.get("items").get(0).get("quantity").asText());
        assertTrue(json.get("items").get(0).get("scrapRate").isTextual());
        assertEquals("0.1234", json.get("items").get(0).get("scrapRate").asText());
    }

    @Test
    void decimalStringsAndScientificNotationDeserializeExactlyIntoRequestBigDecimals() throws Exception {
        BomRequest request = mapper.readValue("{\"productId\":1,\"outputQuantity\":\"99999999999999.9999\","
                + "\"items\":[{\"productId\":2,\"quantity\":\"1e-4\",\"scrapRate\":\"0.1234\"}]}", BomRequest.class);
        assertEquals(new BigDecimal("99999999999999.9999"), request.getOutputQuantity());
        assertEquals(0, new BigDecimal("0.0001").compareTo(request.getItems().get(0).getQuantity()));
        assertEquals(new BigDecimal("0.1234"), request.getItems().get(0).getScrapRate());
    }

    @Test
    void ordinaryLegacyNumericRequestsRemainCompatible() throws Exception {
        BomRequest request = mapper.readValue("{\"productId\":1,\"outputQuantity\":2.5,"
                + "\"items\":[{\"productId\":2,\"quantity\":0.125,\"scrapRate\":1.5}]}", BomRequest.class);
        assertEquals(new BigDecimal("2.5"), request.getOutputQuantity());
        assertEquals(new BigDecimal("0.125"), request.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("1.5"), request.getItems().get(0).getScrapRate());
    }
}
