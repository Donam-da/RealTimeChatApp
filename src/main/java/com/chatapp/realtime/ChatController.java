package com.chatapp.realtime;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;

    // Xử lý gửi tin nhắn từ WebSocket
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        // 1. Lưu tin nhắn vào Database
        chatMessage.setStatus("SENT"); // Mặc định là Đã gửi
        messageRepository.save(chatMessage);

        // 2. Gửi tin nhắn đến ĐÚNG topic của phòng đó (ví dụ: /topic/nam_tuan)
        // Client nào đang subscribe topic này mới nhận được tin nhắn
        messagingTemplate.convertAndSend("/topic/" + chatMessage.getRoomId(), chatMessage);
    }

    // Xử lý thông báo "Đã xem"
    @MessageMapping("/chat.read")
    public void markAsRead(@Payload ChatMessage chatMessage) {
        // chatMessage ở đây đóng vai trò là sự kiện READ, sender là người vừa đọc tin nhắn
        
        // 1. Cập nhật DB: Đánh dấu các tin nhắn trong phòng (mà không phải do mình gửi) thành READ
        List<ChatMessage> messages = messageRepository.findByRoomId(chatMessage.getRoomId());
        for (ChatMessage msg : messages) {
            if (!msg.getSender().equals(chatMessage.getSender()) && !"READ".equals(msg.getStatus())) {
                msg.setStatus("READ");
                messageRepository.save(msg);
            }
        }

        // 2. Gửi sự kiện READ cho client để cập nhật UI
        messagingTemplate.convertAndSend("/topic/" + chatMessage.getRoomId(), chatMessage);
    }

    // Xử lý thông báo "Đang soạn tin"
    @MessageMapping("/chat.typing")
    public void typing(@Payload ChatMessage chatMessage) {
        messagingTemplate.convertAndSend("/topic/" + chatMessage.getRoomId(), chatMessage);
    }

    // API lấy lịch sử tin nhắn của một phòng cụ thể
    @GetMapping("/api/messages/{roomId}")
    public ResponseEntity<List<ChatMessage>> getChatHistory(@PathVariable String roomId) {
        return ResponseEntity.ok(messageRepository.findByRoomId(roomId));
    }

    // API Xóa lịch sử chat của một phòng
    @Transactional
    @DeleteMapping("/api/messages/{roomId}")
    public ResponseEntity<String> deleteChatHistory(@PathVariable String roomId) {
        messageRepository.deleteByRoomId(roomId);
        return ResponseEntity.ok("Đã xóa đoạn chat");
    }
}