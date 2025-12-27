package com.chatapp.realtime;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.LocalDateTime;

public interface MessageRepository extends JpaRepository<ChatMessage, Long> {
    // Tìm tin nhắn theo phòng chat
    List<ChatMessage> findByRoomId(String roomId);

    // Tìm tin nhắn trong phòng nhưng chỉ lấy những tin sau mốc thời gian nhất định
    List<ChatMessage> findByRoomIdAndTimestampAfter(String roomId, LocalDateTime timestamp);

    // Xóa tin nhắn theo phòng chat
    void deleteByRoomId(String roomId);
}