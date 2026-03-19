package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages",
       indexes = {
           @Index(name = "idx_sender_receiver", columnList = "sender, receiver"),
           @Index(name = "idx_receiver_sender", columnList = "receiver, sender")
       })
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String sender;

    @Column(nullable = false, length = 50)
    private String receiver;

    /**
     * For text messages: the message text.
     * For media messages: the server-relative URL (e.g. /uploads/uuid_filename.jpg).
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** "text" or "media" */
    @Column(nullable = false, length = 10)
    private String type;

    /** MIME type for media messages, null for text (e.g. "image/png", "video/mp4") */
    @Column(length = 50)
    private String fileType;

    /** Always serialised as ISO-8601 string so JS Date can parse it reliably */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(nullable = false)
    private LocalDateTime timestamp;


    // ─── Getters / Setters ───────────────────────────────────────

    public Long getId() { return id; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
