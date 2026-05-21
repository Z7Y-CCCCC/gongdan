package com.heihu.workorder.controller;

import com.heihu.workorder.dto.BindFactoryRequest;
import com.heihu.workorder.dto.BindingResponse;
import com.heihu.workorder.service.FactoryBindingService;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/phone")
public class FactoryBindingController {

    private final FactoryBindingService factoryBindingService;

    public FactoryBindingController(FactoryBindingService factoryBindingService) {
        this.factoryBindingService = factoryBindingService;
    }

    @PostMapping("/bind-factory")
    public BindingResponse bindFactory(@Valid @RequestBody BindFactoryRequest request) {
        return factoryBindingService.bindFactory(
                request.getPhone(),
                request.getFactoryCode(),
                request.getAuthorizationCode()
        );
    }
}
