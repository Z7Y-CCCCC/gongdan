package com.heihu.workorder.externaldata;

import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity
@Table(name = "external_data_sources", uniqueConstraints = @UniqueConstraint(name = "uk_external_source_code", columnNames = {"factory_id", "code"}))
public class ExternalSource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "factory_id", nullable = false) public Long factoryId;
    @Column(nullable = false, length = 64) public String code;
    @Column(nullable = false, length = 128) public String name;
    @Column(nullable = false, length = 16) public String kind;
    @Lob @Column(name = "config_json", nullable = false) @JsonIgnore public String configJson;
    @Lob @Column(name = "secret_cipher") @JsonIgnore public String secretCipher;
    @Column(nullable = false) public boolean enabled;
    @Column(nullable = false) public boolean archived;
    @Version @Column(nullable = false) public Long version;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
}
