package com.chatapp.realtime; // Đảm bảo đúng package bạn vừa rename

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity // Đánh dấu để JPA tạo bảng trong MySQL
@Data // Lombok: Tự động tạo Getter, Setter, toString, equals, hashCode
@NoArgsConstructor // Lombok: Tạo constructor không tham số
@AllArgsConstructor // Lombok: Tạo constructor đầy đủ tham số
@Builder // Hỗ trợ tạo đối tượng theo thiết kế Builder pattern
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sender;   // Người gửi
    private String content;  // Nội dung tin nhắn

    @Enumerated(EnumType.STRING)
    private MessageType type; // Loại tin nhắn: CHAT, JOIN, hoặc LEAVE

    private LocalDateTime timestamp; // Thời gian gửi tin nhắn

    // Định nghĩa các loại tin nhắn trong hệ thống chat
    public enum MessageType {
        CHAT, JOIN, LEAVE
    }
}