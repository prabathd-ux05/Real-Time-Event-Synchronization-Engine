package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    // ─── Sign up ────────────────────────────────────────────────
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> body,
                                     HttpSession session) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();

        if (username.isEmpty() || password.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "All fields are required."));
        if (username.length() < 3)
            return ResponseEntity.badRequest().body(Map.of("error", "Username must be at least 3 characters."));
        if (password.length() < 6)
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters."));
        if (userRepository.existsByUsername(username))
            return ResponseEntity.badRequest().body(Map.of("error", "Username is already taken."));

        User user = new User();
        user.setUsername(username);
        user.setPassword(password); // plain-text as per project scope
        userRepository.save(user);

        session.setAttribute("username", username);
        return ResponseEntity.ok(Map.of("username", username));
    }

    // ─── Log in ─────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
                                    HttpSession session) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();

        if (username.isEmpty() || password.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "All fields are required."));

        Optional<User> user = userRepository.findByUsername(username);
        if (user.isEmpty() || !user.get().getPassword().equals(password))
            return ResponseEntity.status(401).body(Map.of("error", "Incorrect username or password."));

        session.setAttribute("username", username);
        return ResponseEntity.ok(Map.of("username", username));
    }

    // ─── Check session ──────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in."));
        return ResponseEntity.ok(Map.of("username", username));
    }

    // ─── Log out ─────────────────────────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out."));
    }
}
