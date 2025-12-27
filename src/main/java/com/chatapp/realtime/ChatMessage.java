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

    private String sender;
    private String content;
    
    // Quan trọng: Lưu mã phòng để biết tin nhắn thuộc cuộc trò chuyện nào
    @Column(name = "room_id")
    private String roomId; 

    private String type; // CHAT, JOIN, LEAVE

    private LocalDateTime timestamp;
}