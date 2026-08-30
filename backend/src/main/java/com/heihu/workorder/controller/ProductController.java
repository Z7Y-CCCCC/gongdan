package com.heihu.workorder.controller;

import com.heihu.workorder.dto.ProductOperationLogRequest;
import com.heihu.workorder.dto.ProductOperationLogResponse;
import com.heihu.workorder.dto.ProductRequest;
import com.heihu.workorder.dto.ProductResponse;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.ProductService;
import com.heihu.workorder.service.TokenService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final TokenService tokenService;

    public ProductController(ProductService productService, TokenService tokenService) {
        this.productService = productService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String spec,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProductResponse> result = productService.list(currentUser.getFactoryId(), code, name, spec, pageRequest);
        Map<String, Object> response = new HashMap<>();
        response.put("items", result.getContent());
        response.put("total", result.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/export")
    public void exportProducts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String spec,
            javax.servlet.http.HttpServletResponse response
    ) throws java.io.IOException {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        String csv = productService.exportProductsCsv(currentUser.getFactoryId(), code, name, spec);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=inventory_balance_export.csv");
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        response.getOutputStream().write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    @GetMapping("/operation-logs")
    public ResponseEntity<Map<String, Object>> listOperationLogs(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProductOperationLogResponse> result = productService.listOperationLogs(currentUser.getFactoryId(), pageRequest);
        Map<String, Object> response = new HashMap<>();
        response.put("items", result.getContent());
        response.put("total", result.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ProductRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(productService.create(
                currentUser.getFactoryId(),
                currentUser.getUserId(),
                currentUser.getName(),
                request
        ));
    }

    @PostMapping("/operation-logs")
    public ResponseEntity<Void> recordOperation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ProductOperationLogRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        productService.recordOperation(currentUser.getFactoryId(), currentUser.getName(), request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody ProductRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        return ResponseEntity.ok(productService.update(
                currentUser.getFactoryId(),
                currentUser.getUserId(),
                currentUser.getName(),
                id,
                request
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        AuthenticatedUser currentUser = tokenService.requireAdmin(authorization);
        productService.delete(currentUser.getFactoryId(), currentUser.getName(), id);
        return ResponseEntity.ok().build();
    }
}
