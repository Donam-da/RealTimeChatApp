package com.chatapp.realtime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

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
        return userRepository.findByUsername(user.getUsername())
                .filter(dbUser -> dbUser.getPassword().equals(user.getPassword()))
                .map(dbUser -> ResponseEntity.ok("Đăng nhập thành công!"))
                .orElse(ResponseEntity.status(401).body("Sai tài khoản hoặc mật khẩu!"));
    }
}