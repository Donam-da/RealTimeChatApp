package com.chatapp.realtime;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRepository extends JpaRepository<ChatMessage, Long> {
    // Nơi chứa các hàm truy vấn dữ liệu sau này
}