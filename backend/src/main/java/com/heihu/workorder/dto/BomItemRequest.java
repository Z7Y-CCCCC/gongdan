package com.heihu.workorder.dto;

import java.math.BigDecimal;

public class BomItemRequest {
    private Long productId;
    private BigDecimal quantity;
    private BigDecimal scrapRate;
    private String remark;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getScrapRate() { return scrapRate; }
    public void setScrapRate(BigDecimal scrapRate) { this.scrapRate = scrapRate; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
