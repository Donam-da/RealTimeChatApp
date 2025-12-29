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
import java.text.Normalizer;
import java.util.regex.Pattern;

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
        
        sendNotificationToPartner(chatMessage);
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
        
        sendNotificationToPartner(chatMessage);
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

    // API Tìm kiếm tin nhắn (Trả về danh sách username và số lượng tin nhắn khớp)
    @GetMapping("/api/messages/search")
    public ResponseEntity<List<Map<String, Object>>> searchMessages(@RequestParam String username, @RequestParam String keyword) {
        // 1. Lấy tất cả tin nhắn liên quan đến user này (thay vì tìm bằng DB để xử lý tiếng Việt chính xác hơn)
        List<ChatMessage> msgs = messageRepository.findByRoomIdContaining(username);
        
        // Lấy danh sách mốc thời gian xóa chat của user để lọc tin nhắn cũ
        List<ChatClearRecord> clearRecords = chatClearRecordRepository.findByUsername(username);
        Map<String, LocalDateTime> clearMap = clearRecords.stream().collect(Collectors.toMap(ChatClearRecord::getRoomId, ChatClearRecord::getClearedAt));

        String normalizedKeyword = normalizeString(keyword);

        // 2. Lọc và đếm số lượng tin nhắn khớp theo từng đối tác
        Map<String, Integer> partnerMatchCounts = new HashMap<>();
        
        for (ChatMessage msg : msgs) {
            // Chỉ tìm trong tin nhắn văn bản (CHAT), bỏ qua IMAGE (base64), TYPING, READ...
            if (!"CHAT".equals(msg.getType())) {
                continue;
            }

            // Bỏ qua tin nhắn đã xóa phía người dùng (Remove for you)
            if (msg.getDeletedBy() != null && msg.getDeletedBy().contains(username + ",")) {
                continue;
            }

            // Kiểm tra nội dung có khớp từ khóa không (Bỏ dấu tiếng Việt)
            String content = msg.getContent();
            if (content == null || !normalizeString(content).contains(normalizedKeyword)) {
                continue;
            }

            String roomId = msg.getRoomId();
            // Kiểm tra xem user hiện tại có trong phòng chat này không (roomId dạng user1_user2)
            if (roomId != null && roomId.contains(username)) {
                String[] parts = roomId.split("_");
                if (parts.length == 2) {
                    String partner = null;
                    if (parts[0].equals(username)) partner = parts[1];
                    else if (parts[1].equals(username)) partner = parts[0];
                    
                    if (partner != null) {
                        // Kiểm tra nếu tin nhắn nằm trước mốc thời gian xóa lịch sử
                        if (clearMap.containsKey(roomId) && msg.getTimestamp().isBefore(clearMap.get(roomId))) {
                            continue;
                        }

                        partnerMatchCounts.put(partner, partnerMatchCounts.getOrDefault(partner, 0) + 1);
                    }
                }
            }
        }
        
        // Chuyển đổi sang List Map để trả về JSON
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : partnerMatchCounts.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("username", entry.getKey());
            item.put("count", entry.getValue());
            results.add(item);
        }
        
        return ResponseEntity.ok(results);
    }

    // API Lấy tin nhắn mới nhất cho danh sách chat (Messenger Style)
    @GetMapping("/api/messages/latest-summaries")
    public ResponseEntity<Map<String, ChatMessage>> getLatestMessages(@RequestParam String username) {
        // 1. Lấy tất cả tin nhắn liên quan đến user này
        List<ChatMessage> allMsgs = messageRepository.findByRoomIdContaining(username);
        
        // Lấy danh sách mốc thời gian xóa chat
        List<ChatClearRecord> clearRecords = chatClearRecordRepository.findByUsername(username);
        Map<String, LocalDateTime> clearMap = clearRecords.stream()
            .collect(Collectors.toMap(ChatClearRecord::getRoomId, ChatClearRecord::getClearedAt));
            
        // Map roomId -> Latest Message
        Map<String, ChatMessage> latestMap = new HashMap<>();
        
        for (ChatMessage msg : allMsgs) {
            // Bỏ qua tin nhắn đã xóa phía người dùng
             if (msg.getDeletedBy() != null && msg.getDeletedBy().contains(username + ",")) {
                continue;
            }
            
            // Bỏ qua tin nhắn trước mốc xóa lịch sử
            if (clearMap.containsKey(msg.getRoomId()) && msg.getTimestamp().isBefore(clearMap.get(msg.getRoomId()))) {
                continue;
            }
            
            String roomId = msg.getRoomId();
            if (!latestMap.containsKey(roomId) || msg.getTimestamp().isAfter(latestMap.get(roomId).getTimestamp())) {
                latestMap.put(roomId, msg);
            }
        }
        
        // Chuyển đổi key từ roomId sang username đối phương
        Map<String, ChatMessage> result = new HashMap<>();
        for (Map.Entry<String, ChatMessage> entry : latestMap.entrySet()) {
            String[] parts = entry.getKey().split("_");
            if (parts.length == 2) {
                String partner = parts[0].equals(username) ? parts[1] : parts[0];
                result.put(partner, entry.getValue());
            }
        }
        
        return ResponseEntity.ok(result);
    }

    // Helper: Chuẩn hóa chuỗi (Bỏ dấu tiếng Việt, về chữ thường)
    private String normalizeString(String input) {
        if (input == null) return "";
        String nfdNormalizedString = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("").toLowerCase().replace("đ", "d");
    }

    // Helper: Gửi thông báo cập nhật danh sách chat cho đối phương
    private void sendNotificationToPartner(ChatMessage msg) {
        String[] parts = msg.getRoomId().split("_");
        if (parts.length == 2) {
            String partner = parts[0].equals(msg.getSender()) ? parts[1] : parts[0];
            messagingTemplate.convertAndSend("/topic/notifications/" + partner, msg);
        }
    }
}