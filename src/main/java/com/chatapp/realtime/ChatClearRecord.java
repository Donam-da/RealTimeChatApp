package com.chatapp.realtime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_clear_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatClearRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String roomId;
    private LocalDateTime clearedAt;
}