package com.chatapp.realtime; // Đúng package của dự án

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // 1. API lấy lịch sử tin nhắn từ MySQL
    @GetMapping("/chat/history")
    @ResponseBody // Để trả về dữ liệu JSON cho trình duyệt
    public List<ChatMessage> getChatHistory() {
        return chatService.getAllMessages();
    }

    // 2. WebSocket: Khi client gửi tới /app/chat.sendMessage
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
        return chatService.saveMessage(chatMessage);
    }

    // 3. WebSocket: Khi có người dùng mới tham gia
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessage addUser(@Payload ChatMessage chatMessage) {
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        return chatService.saveMessage(chatMessage);
    }
}