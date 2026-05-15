package com.heihu.workorder.service;

import com.heihu.workorder.dto.UserPreferenceRequest;
import com.heihu.workorder.dto.UserPreferenceResponse;
import com.heihu.workorder.model.FactoryUser;
import com.heihu.workorder.model.UserPreference;
import com.heihu.workorder.repository.UserRepository;
import com.heihu.workorder.repository.UserPreferenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserPreferenceService {

    @Autowired
    private UserPreferenceRepository preferenceRepository;

    @Autowired
    private UserRepository userRepository;

    public UserPreferenceResponse getPreference(Long userId, String key) {
        FactoryUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return preferenceRepository.findByUserIdAndKey(user.getId(), key)
                .map(this::mapToResponse)
                .orElse(null);
    }

    @Transactional
    public UserPreferenceResponse setPreference(Long userId, String key, UserPreferenceRequest request) {
        FactoryUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserPreference pref = preferenceRepository.findByUserIdAndKey(user.getId(), key)
                .orElse(new UserPreference());
        
        if (pref.getId() == null) {
            pref.setUser(user);
            pref.setKey(key);
        }
        pref.setValue(request.getValue());
        pref.setUpdatedAt(LocalDateTime.now());
        
        pref = preferenceRepository.save(pref);
        return mapToResponse(pref);
    }

    private UserPreferenceResponse mapToResponse(UserPreference pref) {
        UserPreferenceResponse response = new UserPreferenceResponse();
        response.setKey(pref.getKey());
        response.setValue(pref.getValue());
        response.setUpdatedAt(pref.getUpdatedAt());
        return response;
    }
}
