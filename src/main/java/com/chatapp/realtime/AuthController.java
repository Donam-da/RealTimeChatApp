package com.chatapp.realtime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lombok.RequiredArgsConstructor;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // API Đăng ký tài khoản
    @PostMapping("/register")
    public String register(@RequestBody User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return "Tên đăng nhập đã tồn tại!";
        }
        userRepository.save(user); // Lưu vào bảng users
        return "Đăng ký thành công!";
    }

    // API Đăng nhập
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {
        Optional<User> dbUser = userRepository.findByUsername(user.getUsername());
        if (dbUser.isPresent() && dbUser.get().getPassword().equals(user.getPassword())) {
            User loggedInUser = dbUser.get();
            loggedInUser.setStatus("ONLINE");
            userRepository.save(loggedInUser);

            // Thông báo cho mọi người biết user này đã Online
            ChatMessage statusMsg = new ChatMessage();
            statusMsg.setSender(loggedInUser.getUsername());
            statusMsg.setType("STATUS");
            statusMsg.setContent("ONLINE");
            messagingTemplate.convertAndSend("/topic/presence", statusMsg);

            return ResponseEntity.ok(loggedInUser);
        }
        return ResponseEntity.status(401).body("Sai tài khoản hoặc mật khẩu!");
    }

    // API Cập nhật hồ sơ
    @PutMapping("/update")
    public ResponseEntity<?> updateProfile(@RequestBody User updatedUser) {
        Optional<User> dbUser = userRepository.findByUsername(updatedUser.getUsername());
        if (dbUser.isPresent()) {
            User user = dbUser.get();
            if (updatedUser.getDisplayName() != null && !updatedUser.getDisplayName().isEmpty()) {
                user.setDisplayName(updatedUser.getDisplayName());
            }
            if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
                user.setPassword(updatedUser.getPassword());
            }
            if (updatedUser.getAvatar() != null) {
                user.setAvatar(updatedUser.getAvatar());
            }
            userRepository.save(user);
            return ResponseEntity.ok(user);
        }
        return ResponseEntity.badRequest().body("User not found");
    }

    // API Đăng xuất
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody User user) {
        Optional<User> dbUser = userRepository.findByUsername(user.getUsername());
        if (dbUser.isPresent()) {
            User u = dbUser.get();
            u.setStatus("OFFLINE");
            u.setLastActive(LocalDateTime.now());
            userRepository.save(u);

            // Thông báo Offline kèm thời gian
            ChatMessage statusMsg = new ChatMessage();
            statusMsg.setSender(u.getUsername());
            statusMsg.setType("STATUS");
            statusMsg.setContent("OFFLINE");
            statusMsg.setTimestamp(u.getLastActive());
            messagingTemplate.convertAndSend("/topic/presence", statusMsg);
        }
        return ResponseEntity.ok("Đăng xuất thành công");
    }

    // API Lấy danh sách tất cả người dùng
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll();
        // Xóa mật khẩu trước khi trả về client để bảo mật
        users.forEach(u -> u.setPassword(null));
        return ResponseEntity.ok(users);
    }
}