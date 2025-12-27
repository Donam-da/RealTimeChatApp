package com.chatapp.realtime;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<ChatMessage, Long> {
    // Tìm tin nhắn theo phòng chat
    List<ChatMessage> findByRoomId(String roomId);
}