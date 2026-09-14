package com.heihu.workorder.externaldata;

import com.heihu.workorder.service.TokenService;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

@RestController
@RequestMapping("/api/external-data-sources")
public class ExternalSourceController {
    // Do not let generic malformed-request logging include a credential-bearing JSON excerpt.
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.bind.MethodArgumentNotValidException.class})
    public org.springframework.http.ResponseEntity<Map<String,String>> invalidBody(Exception ignored) {
        return org.springframework.http.ResponseEntity.badRequest().body(java.util.Collections.singletonMap("message", "数据源配置格式无效，请检查必填项及字段格式"));
    }
    private final TokenService tokens;
    private final ExternalSourceService service;
    public ExternalSourceController(TokenService tokens, ExternalSourceService service) { this.tokens=tokens; this.service=service; }
    @GetMapping("/capabilities")
    public Map<String,Object> capabilities(@RequestHeader(value="Authorization",required=false) String auth) { return service.capabilities(tokens.requireAdmin(auth)); }
    @GetMapping
    public Map<String,Object> list(@RequestHeader(value="Authorization",required=false) String auth,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="false") boolean archived) { return service.list(tokens.requireAdmin(auth),page,archived); }
    @GetMapping("/{id}")
    public Map<String,Object> get(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable Long id) { return service.get(tokens.requireAdmin(auth),id); }
    @PostMapping
    public Map<String,Object> create(@RequestHeader(value="Authorization",required=false) String auth,@Valid @RequestBody SourceConfig config) { return service.save(tokens.requireAdmin(auth),null,config); }
    @PutMapping("/{id}")
    public Map<String,Object> update(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable Long id,@Valid @RequestBody SourceConfig config) { return service.save(tokens.requireAdmin(auth),id,config); }
    @PostMapping("/test-config")
    public SourceCheck draft(@RequestHeader(value="Authorization",required=false) String auth,@Valid @RequestBody SourceConfig config) { return service.testDraft(tokens.requireAdmin(auth),config); }
    @PostMapping("/{id}/test")
    public SourceCheck test(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable Long id) { return service.testSaved(tokens.requireAdmin(auth),id); }
    public static class VersionRequest { @NotNull public Long version; }
    @PostMapping("/{id}/{action:enable|disable|archive|restore}")
    public Map<String,Object> lifecycle(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable Long id,
        @PathVariable String action,@Valid @RequestBody VersionRequest body) { return service.lifecycle(tokens.requireAdmin(auth),id,body.version,action); }
    @GetMapping("/{id}/checks")
    public Page<SourceCheck> checks(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable Long id,
        @RequestParam(defaultValue="0") int page) { return service.history(tokens.requireAdmin(auth),id,page); }
    @GetMapping("/{id}/schema")
    public Map<String,Object> schema(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable Long id,
        @RequestParam(required=false) String table,@RequestParam(defaultValue="0") int page) { return service.schema(tokens.requireAdmin(auth),id,table,page); }
}
