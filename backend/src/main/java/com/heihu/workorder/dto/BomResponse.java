package com.heihu.workorder.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BomResponse {
    private Long id;
    private String code;
    private Long productId;
    private String productCode;
    private String productName;
    private String productSpec;
    private String unitName;
    private BigDecimal outputQuantity;
    private String remark;
    private boolean enabled;
    private String createdAt;
    private String updatedAt;
    private List<BomItemResponse> items = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

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
    public BigDecimal getOutputQuantity() { return outputQuantity; }
    public void setOutputQuantity(BigDecimal outputQuantity) { this.outputQuantity = outputQuantity; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public List<BomItemResponse> getItems() { return items; }
    public void setItems(List<BomItemResponse> items) { this.items = items; }
}
