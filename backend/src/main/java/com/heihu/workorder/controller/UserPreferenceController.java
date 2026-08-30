package com.heihu.workorder.controller;

import com.heihu.workorder.dto.UserPreferenceRequest;
import com.heihu.workorder.dto.UserPreferenceResponse;
import com.heihu.workorder.service.UserPreferenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/user/preferences")
public class UserPreferenceController {

    @Autowired
    private UserPreferenceService preferenceService;

    @Autowired
    private com.heihu.workorder.service.TokenService tokenService;

    @GetMapping("/{key}")
    public ResponseEntity<UserPreferenceResponse> getPreference(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String key) {
        com.heihu.workorder.service.AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        UserPreferenceResponse res = preferenceService.getPreference(currentUser.getUserId(), key);
        if (res == null) {
            UserPreferenceResponse empty = new UserPreferenceResponse();
            empty.setKey(key);
            return ResponseEntity.ok(empty);
        }
        return ResponseEntity.ok(res);
    }

    @PutMapping("/{key}")
    public ResponseEntity<UserPreferenceResponse> setPreference(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable String key, @RequestBody UserPreferenceRequest request) {
        com.heihu.workorder.service.AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return ResponseEntity.ok(preferenceService.setPreference(currentUser.getUserId(), key, request));
    }
}
