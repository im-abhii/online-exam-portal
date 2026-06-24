package com.examportal.service;

import com.examportal.entity.User;
import com.examportal.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(String firstName, String lastName, String email,
                             String phone, String college, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email is already registered: " + email);
        }

        if (phone != null && !phone.isBlank() && userRepository.existsByPhone(phone)) {
            throw new RuntimeException("Phone number is already registered: " + phone);
        }

        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(phone)
                .college(college)
                .password(passwordEncoder.encode(password))
                .role("ROLE_STUDENT")
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("New student registered: {}", email);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<User> findAllStudents() {
        return userRepository.findByRole("ROLE_STUDENT");
    }

    @Transactional(readOnly = true)
    public List<User> searchStudents(String query) {
        return userRepository.searchStudents("ROLE_STUDENT", query);
    }

    public void toggleUserEnabled(Long id) {
        User user = findById(id);
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        log.info("User {} enabled status toggled to: {}", user.getEmail(), user.isEnabled());
    }

    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        User user = findById(userId);

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return false;
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user: {}", user.getEmail());
        return true;
    }

    @Transactional(readOnly = true)
    public long countStudents() {
        return userRepository.countByRole("ROLE_STUDENT");
    }

    public void updateProfile(Long userId, String firstName, String lastName,
                              String phone, String college) {
        User user = findById(userId);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone(phone);
        user.setCollege(college);
        userRepository.save(user);
        log.info("Profile updated for user: {}", user.getEmail());
    }
}
