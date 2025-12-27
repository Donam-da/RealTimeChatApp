package com.chatapp.realtime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String roomId;
    private String sender;
    
    @Lob
    @Column(columnDefinition = "LONGTEXT") // Quan trọng: Cho phép lưu chuỗi Base64 dài của ảnh
    private String content;
    
    private String type; // CHAT, JOIN, LEAVE, IMAGE, READ, TYPING
    private LocalDateTime timestamp;
    private String status; // SENT, READ
}