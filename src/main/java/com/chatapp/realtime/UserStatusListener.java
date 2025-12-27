package com.chatapp.realtime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UserStatusListener {

    private final UserRepository userRepository;

    // Khi ứng dụng khởi động xong, reset tất cả user về OFFLINE
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if ("ONLINE".equals(user.getStatus())) {
                user.setStatus("OFFLINE");
                user.setLastActive(LocalDateTime.now());
                userRepository.save(user);
            }
        }
        System.out.println("Đã reset trạng thái tất cả người dùng về OFFLINE.");
    }
}