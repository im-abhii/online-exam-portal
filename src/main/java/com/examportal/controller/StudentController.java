package com.examportal.controller;

import com.examportal.entity.ExamAttempt;
import com.examportal.entity.User;
import com.examportal.service.ExamService;
import com.examportal.service.UserService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/student")
public class StudentController {

    private final UserService userService;
    private final ExamService examService;

    public StudentController(UserService userService, ExamService examService) {
        this.userService = userService;
        this.examService = examService;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        User user = getCurrentUser();
        List<ExamAttempt> allAttempts = examService.getStudentAttempts(user.getId());
        List<ExamAttempt> completedAttempts = allAttempts.stream()
                .filter(a -> !"IN_PROGRESS".equals(a.getStatus()))
                .collect(Collectors.toList());

        model.addAttribute("user", user);
        model.addAttribute("availableExams", examService.findAvailableExams());
        model.addAttribute("recentAttempts", completedAttempts.stream().limit(5).collect(Collectors.toList()));
        model.addAttribute("averageScore", examService.getStudentAverageScore(user.getId()));
        model.addAttribute("totalCompleted", completedAttempts.size());
        return "student/dashboard";
    }

    @GetMapping("/exams")
    public String examList(Model model) {
        User user = getCurrentUser();
        List<ExamAttempt> attempts = examService.getStudentAttempts(user.getId());

        // Collect IDs of exams the student has already attempted
        java.util.Set<Long> attemptedExamIds = attempts.stream()
                .map(a -> a.getExam().getId())
                .collect(Collectors.toSet());

        model.addAttribute("user", user);
        model.addAttribute("exams", examService.findAvailableExams());
        model.addAttribute("attemptedExamIds", attemptedExamIds);
        return "student/exam-list";
    }

    @GetMapping("/results")
    public String resultsHistory(Model model) {
        User user = getCurrentUser();
        List<ExamAttempt> attempts = examService.getStudentAttempts(user.getId()).stream()
                .filter(a -> !"IN_PROGRESS".equals(a.getStatus()))
                .collect(Collectors.toList());

        model.addAttribute("user", user);
        model.addAttribute("attempts", attempts);
        return "student/results-history";
    }

    @GetMapping("/change-password")
    public String changePasswordPage(Model model) {
        model.addAttribute("user", getCurrentUser());
        return "student/change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttributes) {
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "New passwords do not match");
            return "redirect:/student/change-password";
        }

        User user = getCurrentUser();
        boolean changed = userService.changePassword(user.getId(), currentPassword, newPassword);

        if (changed) {
            redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Current password is incorrect");
        }
        return "redirect:/student/change-password";
    }
}
