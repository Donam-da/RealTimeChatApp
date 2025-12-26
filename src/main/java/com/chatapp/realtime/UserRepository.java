package com.chatapp.realtime;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    
    // Phương thức quan trọng để kiểm tra đăng nhập
    Optional<User> findByUsername(String username);
}