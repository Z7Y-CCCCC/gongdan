package com.heihu.workorder.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

public class BomItemResponse {
    private Long id;
    private Long productId;
    private String productCode;
    private String productName;
    private String productSpec;
    private String unitName;
    private BigDecimal quantity;
    private BigDecimal scrapRate;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductSpec() { return productSpec; }
    public void setProductSpec(String productSpec) { this.productSpec = productSpec; }

    public String getUnitName() { return unitName; }
    public void setUnitName(String unitName) { this.unitName = unitName; }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public BigDecimal getScrapRate() { return scrapRate; }
    public void setScrapRate(BigDecimal scrapRate) { this.scrapRate = scrapRate; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
