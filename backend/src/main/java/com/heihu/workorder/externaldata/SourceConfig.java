package com.heihu.workorder.externaldata;

import javax.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Closed configuration contract; secrets are only accepted through secretValue. */
@JsonIgnoreProperties(ignoreUnknown = false)
public class SourceConfig {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("不支持的配置字段"); }
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") public String code;
    @NotBlank @Size(max = 128) public String name;
    @Size(max = 500) public String description = "";
    @NotNull @Pattern(regexp = "HTTP|DATABASE") public String kind = "HTTP";
    @Pattern(regexp = "MYSQL|POSTGRESQL|SQLSERVER|SQLITE") public String driver = "MYSQL";
    @Size(max = 1000) public String baseUrl = "";
    @Size(max = 300) public String healthPath = "";
    @Pattern(regexp = "NONE|API_KEY|BEARER|BASIC") public String authType = "NONE";
    @Size(max = 64) public String headerName = "";
    @Size(max = 128) public String username = "";
    @Size(max = 253) public String host = "";
    @Min(1) @Max(65535) public int port = 3306;
    @Size(max = 128) public String database = "";
    @Size(max = 128) public String schema = "";
    @Size(max = 64) public String fileId = "";
    public boolean tls = true;
    @Min(1) @Max(30) public int timeoutSeconds = 5;
    @Pattern(regexp = "KEEP|REPLACE|CLEAR") public String secretAction = "KEEP";
    @Size(max = 4096) public String secretValue = "";
    public Long version;
    public Long sourceId;
}
