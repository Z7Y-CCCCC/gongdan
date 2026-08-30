package com.heihu.workorder.controller;

import com.heihu.workorder.dto.InternalChatContactResponse;
import com.heihu.workorder.dto.InternalChatMessageResponse;
import com.heihu.workorder.dto.SendInternalChatMessageRequest;
import com.heihu.workorder.service.AuthenticatedUser;
import com.heihu.workorder.service.InternalChatService;
import com.heihu.workorder.service.TokenService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal-chat")
public class InternalChatController {

    private final InternalChatService chatService;
    private final TokenService tokenService;

    public InternalChatController(InternalChatService chatService, TokenService tokenService) {
        this.chatService = chatService;
        this.tokenService = tokenService;
    }

    @GetMapping("/contacts")
    public ResponseEntity<List<InternalChatContactResponse>> contacts(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return ResponseEntity.ok(chatService.listContacts(currentUser));
    }

    @GetMapping("/conversations/{peerId}/messages")
    public ResponseEntity<List<InternalChatMessageResponse>> messages(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long peerId
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return ResponseEntity.ok(chatService.listMessages(currentUser, peerId));
    }

    @PostMapping("/messages")
    public ResponseEntity<InternalChatMessageResponse> send(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody SendInternalChatMessageRequest request
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        return ResponseEntity.ok(chatService.sendMessage(currentUser, request));
    }

    @PutMapping("/conversations/{peerId}/read")
    public ResponseEntity<Void> markRead(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long peerId
    ) {
        AuthenticatedUser currentUser = tokenService.requireUser(authorization);
        chatService.markRead(currentUser, peerId);
        return ResponseEntity.noContent().build();
    }
}
