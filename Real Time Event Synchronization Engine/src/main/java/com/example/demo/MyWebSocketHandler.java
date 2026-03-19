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

    // ─────────────────────────────────────────────────────────────
    //  DEPENDENCIES
    // ─────────────────────────────────────────────────────────────

    @Autowired
    private MessageRepository messageRepository;

    // ─────────────────────────────────────────────────────────────
    //  STATE
    // ─────────────────────────────────────────────────────────────

    /** Maps username → live WebSocket session. Thread-safe. */
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

    private static final int MAX_CONTENT_LENGTH = 2000;

    // ─────────────────────────────────────────────────────────────
    //  LIFECYCLE
    // ─────────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // The HttpSessionHandshakeInterceptor copies the HTTP session's "username"
        // attribute into the WS session before this method runs.
        // So authenticated users are auto-registered without a separate setUsername event.
        String username = getSessionUsername(session);
        if (username != null) {
            userSessions.put(username, session);
            System.out.println("[WS] Auto-registered (session): " + username);

            // ① Send snapshot of all currently online users
            sendUserListSnapshot(session, username);

            // ② Announce arrival to everyone else
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

    // ─────────────────────────────────────────────────────────────
    //  MESSAGE DISPATCH
    // ─────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────
    //  HANDLERS
    // ─────────────────────────────────────────────────────────────

    /** Fallback registration for unauthenticated (non-session) connections. */
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

    /** Routes a private text message from sender to recipient. Persists to DB. */
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

        // ── Persist ──────────────────────────────────────────────
        Message entity = new Message();
        entity.setSender(sender);
        entity.setReceiver(to);
        entity.setContent(content);
        entity.setType("text");
        entity.setTimestamp(now);
        messageRepository.save(entity);

        // ── Build WS payload ─────────────────────────────────────
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type",      "message");
        msg.put("from",      sender);
        msg.put("to",        to);
        msg.put("content",   content);
        msg.put("timestamp", now.format(timeFormatter));

        // Deliver to recipient
        WebSocketSession recipientSession = userSessions.get(to);
        if (recipientSession != null && recipientSession.isOpen())
            sendSafe(recipientSession, msg);

        // Echo to sender
        if (!sender.equals(to))
            sendSafe(session, msg);
    }

    /**
     * Routes a media message.
     * Content is a server URL (e.g. /uploads/uuid.jpg) — NOT base64.
     * The client uploads the file via REST first, then sends only the URL here.
     */
    private void handleMediaMessage(WebSocketSession session, JsonNode json) throws IOException {
        String sender = getSessionUsername(session);
        if (sender == null) return;
        if (!json.has("to") || !json.has("content") || !json.has("fileType")) return;

        String to       = json.get("to").asText("").trim();
        String content  = json.get("content").asText("").trim();  // server URL
        String fileType = json.get("fileType").asText("").trim();
        if (to.isEmpty() || content.isEmpty() || fileType.isEmpty()) return;

        // Reject if someone accidentally sent a raw base64 payload (safety valve)
        if (content.startsWith("data:") && content.length() > 500) {
            System.err.println("[WS] Rejected raw base64 media from " + sender + ". Use /api/upload first.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // ── Persist ──────────────────────────────────────────────
        Message entity = new Message();
        entity.setSender(sender);
        entity.setReceiver(to);
        entity.setContent(content);
        entity.setType("media");
        entity.setFileType(fileType);
        entity.setTimestamp(now);
        messageRepository.save(entity);

        // ── Build WS payload ─────────────────────────────────────
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

    /** Forwards typing / stopTyping events only to the intended recipient. */
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

    // ─────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────

    /** Sends the new user a list of everyone already online. */
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

    /** Broadcasts a userJoined / userLeft event to every live session. Prunes closed ones safely. */
    private void broadcastStatusSafe(String username, boolean isOnline) {
        ObjectNode statusMsg = objectMapper.createObjectNode();
        // Match the frontend switch: case "userJoined" / case "userLeft"
        statusMsg.put("type",     isOnline ? "userJoined" : "userLeft");
        statusMsg.put("username", username);

        // Collect stale sessions separately — don't mutate map during iteration
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

    /** Serialises and sends a JSON node. Swallows IOException to keep broadcast loops clean. */
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
