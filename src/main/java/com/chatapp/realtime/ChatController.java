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
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

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

    // Xử lý Thả cảm xúc (Reaction)
    @MessageMapping("/chat.react")
    public void reactToMessage(@Payload ChatMessage chatMessage) {
        Optional<ChatMessage> msgOpt = messageRepository.findById(chatMessage.getId());
        if (msgOpt.isPresent()) {
            ChatMessage msg = msgOpt.get();
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, String> reactions = new HashMap<>();
                if (msg.getReactions() != null && !msg.getReactions().isEmpty()) {
                    reactions = mapper.readValue(msg.getReactions(), new TypeReference<Map<String, String>>(){});
                }
                
                // chatMessage.getContent() chứa emoji (ví dụ: "👍")
                // Cập nhật cảm xúc (ghi đè nếu đã có, không xóa khi chọn trùng)
                reactions.put(chatMessage.getSender(), chatMessage.getContent());
                
                msg.setReactions(mapper.writeValueAsString(reactions));
                messageRepository.save(msg);
                
                // Gửi tin nhắn cập nhật về client với type là REACT
                msg.setType("REACT");
                messagingTemplate.convertAndSend("/topic/" + msg.getRoomId(), msg);
            } catch (Exception e) {
                e.printStackTrace();
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

    // API Tìm kiếm tin nhắn (Trả về danh sách username của đối phương có tin nhắn khớp)
    @GetMapping("/api/messages/search")
    public ResponseEntity<List<String>> searchMessages(@RequestParam String username, @RequestParam String keyword) {
        // 1. Tìm tất cả tin nhắn chứa từ khóa
        List<ChatMessage> msgs = messageRepository.findByContentContainingIgnoreCase(keyword);
        
        // 2. Lọc ra các username đối phương trong các cuộc trò chuyện đó
        Set<String> partners = new HashSet<>();
        for (ChatMessage msg : msgs) {
            String roomId = msg.getRoomId();
            // Kiểm tra xem user hiện tại có trong phòng chat này không (roomId dạng user1_user2)
            if (roomId != null && roomId.contains(username)) {
                String[] parts = roomId.split("_");
                if (parts.length == 2) {
                    if (parts[0].equals(username)) partners.add(parts[1]);
                    else if (parts[1].equals(username)) partners.add(parts[0]);
                }
            }
        }
        return ResponseEntity.ok(new ArrayList<>(partners));
    }
}