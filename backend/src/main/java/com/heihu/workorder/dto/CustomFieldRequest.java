package com.heihu.workorder.dto;

import java.util.List;

public class CustomFieldRequest {

    private String scope;
    private String name;
    private String fieldType;
    private List<OptionItem> options;
    private List<String> defaultValues;
    private String placeholder;
    private String displayMode;
    private Boolean required;
    private Boolean allowUserOptions;
    private String helperText;
    private String description;
    private Integer sortOrder;

    public static class OptionItem {
        private String label;
        private String value;
        private String color;
        private Boolean defaultSelected;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public Boolean getDefaultSelected() {
            return defaultSelected;
        }

        public void setDefaultSelected(Boolean defaultSelected) {
            this.defaultSelected = defaultSelected;
        }
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

    public List<OptionItem> getOptions() {
        return options;
    }

    public void setOptions(List<OptionItem> options) {
        this.options = options;
    }

    public List<String> getDefaultValues() {
        return defaultValues;
    }

    public void setDefaultValues(List<String> defaultValues) {
        this.defaultValues = defaultValues;
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
}
