package com.heihu.workorder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.heihu.workorder.model.CustomField;
import com.heihu.workorder.repository.CustomFieldRepository;
import com.heihu.workorder.util.DateTimeParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Server-side field semantics; historical missing values can remain unchanged on edits. */
@Component
public class CustomFieldValueValidator {
    private final CustomFieldRepository fields;
    private final ObjectMapper mapper;
    public CustomFieldValueValidator(CustomFieldRepository fields, ObjectMapper mapper) { this.fields = fields; this.mapper = mapper; }

    public void validate(Long factoryId, String scope, Object supplied, String previousJson, boolean allowLegacyMissing) {
        JsonNode values = supplied == null ? mapper.createArrayNode() : mapper.valueToTree(supplied);
        JsonNode previous = readArray(previousJson);
        for (CustomField field : fields.findByFactoryIdAndScopeOrderBySortOrderAscCreatedAtAsc(factoryId, scope)) {
            List<JsonNode> matching = matches(values, field);
            if (matching.size() > 1) throw invalid("Duplicate field value", field);
            JsonNode item = matching.isEmpty() ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : matching.get(0);
            JsonNode value = item.path("value");
            List<JsonNode> oldItems = matches(previous, field);
            JsonNode oldValue = oldItems.isEmpty() ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : oldItems.get(0).path("value");
            if (empty(value)) {
                if (Boolean.TRUE.equals(field.getRequired()) && !(allowLegacyMissing && empty(oldValue))) throw invalid("Required field", field);
                continue;
            }
            // Do not invalidate unchanged historical values when a definition has since changed.
            if (allowLegacyMissing && value.equals(oldValue)) continue;
            String type = field.getFieldType();
            String declared = item.path("fieldType").asText("");
            if (!declared.isEmpty() && !declared.equals(type)) throw invalid("Field type mismatch", field);
            if ("text".equals(type) && !value.isTextual()) throw invalid("Invalid text", field);
            if ("file".equals(type) || "image".equals(type)) {
                if (!value.isTextual() && !value.isArray()) throw invalid("Invalid attachment", field);
                if (value.isArray()) for (JsonNode file : value) if (!file.isTextual()) throw invalid("Invalid attachment", field);
            }
            if ("number".equals(type)) {
                if (!value.isNumber() && !value.isTextual()) throw invalid("Invalid number", field);
                try { new BigDecimal(value.asText().trim()); } catch (NumberFormatException exception) { throw invalid("Invalid number", field); }
            }
            if ("date".equals(type)) {
                if (!value.isTextual()) throw invalid("Invalid date", field);
                try { LocalDate.parse(value.asText()); }
                catch (RuntimeException dateOnly) { try { DateTimeParser.parse(value.asText()); } catch (RuntimeException dateTime) { throw invalid("Invalid date", field); } }
            }
            if ("single_select".equals(type) || "multiple_select".equals(type)) {
                List<String> selected = new ArrayList<>();
                if (value.isTextual() && "single_select".equals(type)) selected.add(value.asText());
                else if (value.isArray()) {
                    for (JsonNode choice : value) { if (!choice.isTextual()) throw invalid("Invalid selection", field); selected.add(choice.asText()); }
                } else throw invalid("Invalid selection", field);
                if ("single_select".equals(type) && selected.size() > 1) throw invalid("Invalid selection", field);
                Set<String> allowed = new HashSet<>();
                for (JsonNode option : readArray(field.getOptionsJson())) allowed.add(option.path("value").asText(option.path("label").asText()));
                if (!Boolean.TRUE.equals(field.getAllowUserOptions()) && !allowed.containsAll(selected)) throw invalid("Invalid selection", field);
            }
        }
    }

    private List<JsonNode> matches(JsonNode values, CustomField field) {
        List<JsonNode> result = new ArrayList<>();
        if (values != null && values.isArray()) for (JsonNode value : values) {
            JsonNode id = value.path("fieldId");
            if (!id.isNull() && !id.isMissingNode() ? id.asLong() == field.getId() : field.getName().equals(value.path("fieldName").asText())) result.add(value);
        }
        return result;
    }
    private ArrayNode readArray(String json) {
        try { JsonNode value = mapper.readTree(json == null ? "[]" : json); if (value != null && value.isArray()) return (ArrayNode) value; }
        catch (java.io.IOException ignored) { }
        return mapper.createArrayNode();
    }
    private boolean empty(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode() || value.isTextual() && value.asText().trim().isEmpty()
                || (value.isArray() || value.isObject()) && value.size() == 0;
    }
    private IllegalArgumentException invalid(String problem, CustomField field) { return new IllegalArgumentException(problem + ": " + field.getName()); }
}
