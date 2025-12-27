package com.chatapp.realtime;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ChatClearRecordRepository extends JpaRepository<ChatClearRecord, Long> {
    Optional<ChatClearRecord> findByUsernameAndRoomId(String username, String roomId);
}