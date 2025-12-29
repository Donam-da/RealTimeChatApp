package com.chatapp.realtime;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface ChatClearRecordRepository extends JpaRepository<ChatClearRecord, Long> {
    Optional<ChatClearRecord> findByUsernameAndRoomId(String username, String roomId);
    List<ChatClearRecord> findByUsername(String username);
}