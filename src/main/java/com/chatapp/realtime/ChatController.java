package com.chatapp.realtime;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;
    private final ChatClearRecordRepository chatClearRecordRepository;

    // Xử lý gửi tin nhắn từ WebSocket
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        // 1. Lưu tin nhắn vào Database
        chatMessage.setStatus("SENT"); // Mặc định là Đã gửi
        chatMessage.setTimestamp(LocalDateTime.now()); // Đảm bảo có thời gian gửi
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

    // Xử lý Thu hồi tin nhắn (Unsend for everyone)
    @MessageMapping("/chat.revoke")
    public void revokeMessage(@Payload ChatMessage chatMessage) {
        Optional<ChatMessage> msgOpt = messageRepository.findById(chatMessage.getId());
        if (msgOpt.isPresent()) {
            ChatMessage msg = msgOpt.get();
            // Chỉ người gửi mới được thu hồi
            if (msg.getSender().equals(chatMessage.getSender())) {
                msg.setType("REVOKED");
                msg.setContent("Tin nhắn đã bị thu hồi");
                messageRepository.save(msg);
                
                // Gửi thông báo cập nhật cho mọi người
                messagingTemplate.convertAndSend("/topic/" + msg.getRoomId(), msg);
            }
        }
    }

    // API Xóa tin nhắn phía người dùng (Remove for you)
    @DeleteMapping("/api/messages/single/{messageId}")
    public ResponseEntity<?> deleteMessageForUser(@PathVariable Long messageId, @RequestParam String username) {
        Optional<ChatMessage> msgOpt = messageRepository.findById(messageId);
        if (msgOpt.isPresent()) {
            ChatMessage msg = msgOpt.get();
            String currentDeleted = msg.getDeletedBy() == null ? "" : msg.getDeletedBy();
            // Thêm username vào danh sách đã xóa (ngăn cách bằng dấu phẩy)
            msg.setDeletedBy(currentDeleted + username + ",");
            messageRepository.save(msg);
            return ResponseEntity.ok("Đã xóa tin nhắn phía bạn");
        }
        return ResponseEntity.badRequest().body("Tin nhắn không tồn tại");
    }

    // API lấy lịch sử tin nhắn của một phòng cụ thể
    @GetMapping("/api/messages/{roomId}")
    public ResponseEntity<List<ChatMessage>> getChatHistory(@PathVariable String roomId, @RequestParam String username) {
        // Kiểm tra xem user này đã từng xóa lịch sử chat chưa
        Optional<ChatClearRecord> record = chatClearRecordRepository.findByUsernameAndRoomId(username, roomId);
        if (record.isPresent()) {
            // Nếu có, chỉ trả về tin nhắn SAU thời điểm xóa
            List<ChatMessage> messages = messageRepository.findByRoomIdAndTimestampAfter(roomId, record.get().getClearedAt());
            return ResponseEntity.ok(filterDeletedMessages(messages, username));
        }
        // Nếu chưa xóa bao giờ, trả về toàn bộ
        List<ChatMessage> messages = messageRepository.findByRoomId(roomId);
        return ResponseEntity.ok(filterDeletedMessages(messages, username));
    }

    // Helper: Lọc bỏ các tin nhắn mà user này đã chọn "Xóa ở phía bạn"
    private List<ChatMessage> filterDeletedMessages(List<ChatMessage> messages, String username) {
        return messages.stream()
                .filter(m -> m.getDeletedBy() == null || !m.getDeletedBy().contains(username + ","))
                .collect(Collectors.toList());
    }

    // API Xóa lịch sử chat (Chỉ ẩn với người dùng hiện tại)
    @Transactional
    @DeleteMapping("/api/messages/{roomId}")
    public ResponseEntity<String> deleteChatHistory(@PathVariable String roomId, @RequestParam String username) {
        Optional<ChatClearRecord> recordOpt = chatClearRecordRepository.findByUsernameAndRoomId(username, roomId);
        ChatClearRecord record = recordOpt.orElse(new ChatClearRecord(null, username, roomId, null));
        record.setClearedAt(LocalDateTime.now()); // Cập nhật mốc thời gian xóa là hiện tại
        chatClearRecordRepository.save(record);
        
        return ResponseEntity.ok("Đã xóa đoạn chat (phía bạn)");
    }
}