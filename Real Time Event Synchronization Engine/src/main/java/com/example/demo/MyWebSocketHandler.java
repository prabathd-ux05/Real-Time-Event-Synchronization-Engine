package com.example.demo;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.demo.model.Message;
import com.example.demo.repository.MessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class MyWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private MessageRepository messageRepository;

    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

    private static final int MAX_CONTENT_LENGTH = 2000;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String username = getSessionUsername(session);
        if (username != null) {
            userSessions.put(username, session);
            System.out.println("[WS] Auto-registered (session): " + username);

            sendUserListSnapshot(session, username);

            broadcastStatusSafe(username, true);
        } else {
            System.out.println("[WS] Anonymous connection: " + session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String username = getSessionUsername(session);
        if (username != null) {
            userSessions.remove(username);
            System.out.println("[WS] Disconnected: " + username);
            broadcastStatusSafe(username, false);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String username = getSessionUsername(session);
        System.err.println("[WS] Transport error for "
                + (username != null ? username : session.getId())
                + ": " + exception.getMessage());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode json = objectMapper.readTree(message.getPayload());
            String type = json.has("type") ? json.get("type").asText("") : "message";

            switch (type) {
                // Fallback for non-HTTP-session clients (e.g. manual WS tools)
                case "setUsername" -> handleSetUsername(session, json);

                case "message"     -> handleChatMessage(session, json);
                case "media"       -> handleMediaMessage(session, json);

                case "typing",
                     "stopTyping" -> handleTyping(session, json, type);

                default -> System.out.println("[WS] Unknown type: " + type);
            }

        } catch (Exception e) {
            System.err.println("[WS] Error: " + e.getMessage());
        }
    }

    private void handleSetUsername(WebSocketSession session, JsonNode json) throws IOException {
        if (!json.has("username")) return;
        String username = json.get("username").asText("").trim();
        if (username.isEmpty()) return;

        userSessions.put(username, session);
        session.getAttributes().put("username", username);
        System.out.println("[WS] Registered (setUsername): " + username);

        sendUserListSnapshot(session, username);
        broadcastStatusSafe(username, true);
    }

    private void handleChatMessage(WebSocketSession session, JsonNode json) throws IOException {
        String sender = getSessionUsername(session);
        if (sender == null) return;
        if (!json.has("to") || !json.has("content")) return;

        String to      = json.get("to").asText("").trim();
        String content = json.get("content").asText("").trim();
        if (to.isEmpty() || content.isEmpty()) return;

        if (content.length() > MAX_CONTENT_LENGTH)
            content = content.substring(0, MAX_CONTENT_LENGTH);

        LocalDateTime now = LocalDateTime.now();

        Message entity = new Message();
        entity.setSender(sender);
        entity.setReceiver(to);
        entity.setContent(content);
        entity.setType("text");
        entity.setTimestamp(now);
        messageRepository.save(entity);

        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type",      "message");
        msg.put("from",      sender);
        msg.put("to",        to);
        msg.put("content",   content);
        msg.put("timestamp", now.format(timeFormatter));

        WebSocketSession recipientSession = userSessions.get(to);
        if (recipientSession != null && recipientSession.isOpen())
            sendSafe(recipientSession, msg);

        if (!sender.equals(to))
            sendSafe(session, msg);
    }
    
    private void handleMediaMessage(WebSocketSession session, JsonNode json) throws IOException {
        String sender = getSessionUsername(session);
        if (sender == null) return;
        if (!json.has("to") || !json.has("content") || !json.has("fileType")) return;

        String to       = json.get("to").asText("").trim();
        String content  = json.get("content").asText("").trim();  
        String fileType = json.get("fileType").asText("").trim();
        if (to.isEmpty() || content.isEmpty() || fileType.isEmpty()) return;

        if (content.startsWith("data:") && content.length() > 500) {
            System.err.println("[WS] Rejected raw base64 media from " + sender + ". Use /api/upload first.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        Message entity = new Message();
        entity.setSender(sender);
        entity.setReceiver(to);
        entity.setContent(content);
        entity.setType("media");
        entity.setFileType(fileType);
        entity.setTimestamp(now);
        messageRepository.save(entity);

        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type",      "media");
        msg.put("from",      sender);
        msg.put("to",        to);
        msg.put("content",   content);
        msg.put("fileType",  fileType);
        msg.put("timestamp", now.format(timeFormatter));

        WebSocketSession recipientSession = userSessions.get(to);
        if (recipientSession != null && recipientSession.isOpen())
            sendSafe(recipientSession, msg);

        if (!sender.equals(to))
            sendSafe(session, msg);
    }

    private void handleTyping(WebSocketSession session, JsonNode json, String type) {
        String from = getSessionUsername(session);
        if (from == null || !json.has("to")) return;

        String to = json.get("to").asText("").trim();
        if (to.isEmpty()) return;

        ObjectNode eventMsg = objectMapper.createObjectNode();
        eventMsg.put("type",     type);
        eventMsg.put("username", from);

        WebSocketSession recipientSession = userSessions.get(to);
        if (recipientSession != null && recipientSession.isOpen())
            sendSafe(recipientSession, eventMsg);
    }

    private void sendUserListSnapshot(WebSocketSession session, String username) {
        ArrayNode usersArray = objectMapper.createArrayNode();
        for (String u : userSessions.keySet()) {
            if (!u.equals(username)) usersArray.add(u);
        }
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type", "userList");
        msg.set("users", usersArray);
        sendSafe(session, msg);
    }

    private void broadcastStatusSafe(String username, boolean isOnline) {
        ObjectNode statusMsg = objectMapper.createObjectNode();
        statusMsg.put("type",     isOnline ? "userJoined" : "userLeft");
        statusMsg.put("username", username);

        java.util.List<String> toRemove = new java.util.ArrayList<>();

        for (Map.Entry<String, WebSocketSession> entry : userSessions.entrySet()) {
            WebSocketSession s = entry.getValue();
            if (s.isOpen()) {
                sendSafe(s, statusMsg);
            } else {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(userSessions::remove);
    }

    private void sendSafe(WebSocketSession session, ObjectNode payload) {
        if (session == null || !session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException e) {
            System.err.println("[WS] Send failed → " + session.getId() + ": " + e.getMessage());
        }
    }

    private String getSessionUsername(WebSocketSession session) {
        return (String) session.getAttributes().get("username");
    }
}
