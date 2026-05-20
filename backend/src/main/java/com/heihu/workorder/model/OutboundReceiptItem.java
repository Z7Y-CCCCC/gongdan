package com.heihu.workorder.model;

import javax.persistence.*;

@Entity
@Table(name = "outbound_receipt_items")
public class OutboundReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outbound_receipt_id", nullable = false)
    private OutboundReceipt outboundReceipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Lob
    @Column(name = "custom_data_json")
    private String customDataJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public OutboundReceipt getOutboundReceipt() { return outboundReceipt; }
    public void setOutboundReceipt(OutboundReceipt outboundReceipt) { this.outboundReceipt = outboundReceipt; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getCustomDataJson() { return customDataJson; }
    public void setCustomDataJson(String customDataJson) { this.customDataJson = customDataJson; }
}
