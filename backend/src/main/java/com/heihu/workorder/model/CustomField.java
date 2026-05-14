package com.heihu.workorder.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "custom_fields",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_custom_field_name", columnNames = {"factory_id", "scope_name", "field_name"})
        }
)
public class CustomField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factory_id", nullable = false)
    private Factory factory;

    @Column(name = "scope_name", nullable = false, length = 32)
    private String scope;

    @Column(name = "field_name", nullable = false, length = 64)
    private String name;

    @Column(name = "field_type", nullable = false, length = 32)
    private String fieldType;

    @Lob
    @Column(name = "options_json")
    private String optionsJson;

    @Lob
    @Column(name = "default_value_json")
    private String defaultValueJson;

    @Column(length = 64)
    private String placeholder;

    @Column(name = "display_mode", length = 32)
    private String displayMode;

    @Column(nullable = false)
    private Boolean required = Boolean.FALSE;

    @Column(name = "allow_user_options", nullable = false)
    private Boolean allowUserOptions = Boolean.FALSE;

    @Column(name = "helper_text", length = 128)
    private String helperText;

    @Column(name = "description_text", length = 255)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private FactoryUser creator;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Factory getFactory() {
        return factory;
    }

    public void setFactory(Factory factory) {
        this.factory = factory;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public void setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
    }

    public String getDefaultValueJson() {
        return defaultValueJson;
    }

    public void setDefaultValueJson(String defaultValueJson) {
        this.defaultValueJson = defaultValueJson;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public String getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(String displayMode) {
        this.displayMode = displayMode;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getAllowUserOptions() {
        return allowUserOptions;
    }

    public void setAllowUserOptions(Boolean allowUserOptions) {
        this.allowUserOptions = allowUserOptions;
    }

    public String getHelperText() {
        return helperText;
    }

    public void setHelperText(String helperText) {
        this.helperText = helperText;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public FactoryUser getCreator() {
        return creator;
    }

    public void setCreator(FactoryUser creator) {
        this.creator = creator;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
