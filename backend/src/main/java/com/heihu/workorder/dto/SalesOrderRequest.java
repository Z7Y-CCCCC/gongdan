package com.heihu.workorder.dto;

import java.util.ArrayList;
import java.util.List;

public class SalesOrderRequest {
    private String code;
    private String customerName;
    private String orderDate;
    private String deliveryDate;
    private String status;
    private String remark;
    private List<SalesOrderItemRequest> items = new ArrayList<>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    public String getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(String deliveryDate) { this.deliveryDate = deliveryDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public List<SalesOrderItemRequest> getItems() { return items; }
    public void setItems(List<SalesOrderItemRequest> items) { this.items = items; }
}
