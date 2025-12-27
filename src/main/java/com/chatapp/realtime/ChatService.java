package com.chatapp.realtime;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;

    public ChatMessage saveMessage(ChatMessage message) {
        if (message == null) return null;
        message.setTimestamp(LocalDateTime.now()); 
        return chatRepository.save(message);
    }

    public List<ChatMessage> getAllMessages() {
        return chatRepository.findAll();
    }

    // THÊM HÀM XÓA: Xóa sạch tin nhắn trong MySQL
    public void clearAllMessages() {
        chatRepository.deleteAll();
        log.info("Toàn bộ lịch sử chat đã bị xóa.");
    }
}