package com.heihu.workorder.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "process_routes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_route_code", columnNames = {"factory_id", "code"})
        })
public class ProcessRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factory_id", nullable = false)
    private Factory factory;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    // 工序步骤，JSON数组存储
    // 格式: [{"type":"process","processId":1,"reportRatio":1,"remark":"..."}]
    @Lob
    @Column(name = "steps")
    private String steps;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private FactoryUser creator;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Factory getFactory() { return factory; }
    public void setFactory(Factory factory) { this.factory = factory; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSteps() { return steps; }
    public void setSteps(String steps) { this.steps = steps; }

    public FactoryUser getCreator() { return creator; }
    public void setCreator(FactoryUser creator) { this.creator = creator; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
