package com.example.demo.controller;

import com.example.demo.model.Message;
import com.example.demo.repository.MessageRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MessageController {

    @Autowired
    private MessageRepository messageRepository;

    /**
     * GET /api/messages?with={username}
     * Returns the full conversation between the logged-in user and the target.
     */
    @GetMapping("/messages")
    public ResponseEntity<?> getMessages(@RequestParam("with") String partner,
                                          HttpSession session) {
        String me = (String) session.getAttribute("username");
        if (me == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in."));

        List<Message> messages = messageRepository.findConversation(me, partner);
        return ResponseEntity.ok(messages);
    }

    /**
     * GET /api/conversations
     * Returns the list of distinct usernames the logged-in user has ever chatted with.
     * Called once after login to pre-populate the sidebar chat list.
     */
    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(HttpSession session) {
        String me = (String) session.getAttribute("username");
        if (me == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in."));

        List<String> partners = messageRepository.findChatPartners(me);
        return ResponseEntity.ok(partners);
    }
}

