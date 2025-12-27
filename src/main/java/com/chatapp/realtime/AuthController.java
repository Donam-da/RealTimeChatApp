package com.chatapp.realtime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

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
            return ResponseEntity.ok(dbUser.get());
        }
        return ResponseEntity.status(401).body("Sai tài khoản hoặc mật khẩu!");
    }
}