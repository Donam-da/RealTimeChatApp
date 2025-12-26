package com.chatapp.realtime; // Đảm bảo đúng package đã rename

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j // Hỗ trợ ghi Log để theo dõi tin nhắn ở Console
@Service
@RequiredArgsConstructor // Tự động tạo Constructor để Inject ChatRepository (chuẩn hơn @Autowired field)
public class ChatService {

    private final ChatRepository chatRepository;

    /**
     * Lưu tin nhắn mới và gán thời gian thực tế
     */
    public ChatMessage saveMessage(ChatMessage message) {
        if (message == null) {
            log.error("Cố gắng lưu một tin nhắn null");
            return null;
        }
        
        // Thiết lập thời gian gửi tin nhắn trước khi lưu
        message.setTimestamp(LocalDateTime.now()); 
        
        log.info("Lưu tin nhắn từ: {}", message.getSender());
        return chatRepository.save(message);
    }

    /**
     * Lấy toàn bộ lịch sử tin nhắn từ database chat_app_db
     */
    public List<ChatMessage> getAllMessages() {
        return chatRepository.findAll();
    }
}