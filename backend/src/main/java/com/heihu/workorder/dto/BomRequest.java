package com.heihu.workorder.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BomRequest {
    private String code;
    private Long productId;
    private BigDecimal outputQuantity;
    private String remark;
    private Boolean enabled;
    private List<BomItemRequest> items = new ArrayList<>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public BigDecimal getOutputQuantity() { return outputQuantity; }
    public void setOutputQuantity(BigDecimal outputQuantity) { this.outputQuantity = outputQuantity; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public List<BomItemRequest> getItems() { return items; }
    public void setItems(List<BomItemRequest> items) { this.items = items; }
}
