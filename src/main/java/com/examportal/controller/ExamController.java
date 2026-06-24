package com.examportal.controller;

import com.examportal.entity.*;
import com.examportal.service.ExamService;
import com.examportal.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/exam")
@Slf4j
public class ExamController {

    private final ExamService examService;
    private final UserService userService;

    public ExamController(ExamService examService, UserService userService) {
        this.examService = examService;
        this.userService = userService;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping("/instructions/{examId}")
    public String instructions(@PathVariable Long examId, Model model) {
        User user = getCurrentUser();
        Exam exam = examService.findExamById(examId);
        int questionCount = examService.countQuestionsByExamId(examId);
        boolean alreadyAttempted = examService.hasAttempted(user.getId(), examId);

        model.addAttribute("exam", exam);
        model.addAttribute("questionCount", questionCount);
        model.addAttribute("alreadyAttempted", alreadyAttempted);
        model.addAttribute("user", user);
        return "student/exam-instructions";
    }

    @PostMapping("/start/{examId}")
    public String startExam(@PathVariable Long examId, RedirectAttributes redirectAttributes) {
        User user = getCurrentUser();
        try {
            ExamAttempt attempt = examService.startAttempt(user.getId(), examId);
            return "redirect:/exam/window/" + attempt.getId();
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/exam/instructions/" + examId;
        }
    }

    @GetMapping("/window/{attemptId}")
    public String examWindow(@PathVariable Long attemptId, Model model) {
        User user = getCurrentUser();
        ExamAttempt attempt = examService.getAttempt(attemptId);

        // Verify ownership
        if (!attempt.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized access to exam attempt");
        }

        // If already submitted, redirect to result
        if (!"IN_PROGRESS".equals(attempt.getStatus())) {
            return "redirect:/exam/result/" + attemptId;
        }

        Exam exam = attempt.getExam();

        // Calculate remaining time
        long elapsedSeconds = ChronoUnit.SECONDS.between(attempt.getStartTime(), LocalDateTime.now());
        long totalSeconds = (long) exam.getDurationMinutes() * 60;
        long remainingSeconds = totalSeconds - elapsedSeconds;

        // Auto-submit if time is up
        if (remainingSeconds <= 0) {
            examService.submitAttempt(attemptId, true);
            return "redirect:/exam/result/" + attemptId;
        }

        // Parse question order and option mappings
        List<Long> questionOrder = examService.parseQuestionOrder(attempt.getQuestionOrder());
        Map<Long, String> optionMappings = examService.parseOptionMappings(attempt.getOptionMappings());

        // Build questions data structure for frontend
        Map<Long, Question> questionMap = examService.findQuestionsByExamId(exam.getId()).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // Get existing answers
        Map<Long, StudentAnswer> answersMap = examService.getAnswersMap(attemptId);

        // Build questionsData JSON string
        String questionsData = buildQuestionsJson(questionOrder, questionMap, optionMappings);

        // Build answersData JSON string (with display options, not original)
        String answersData = buildAnswersJson(answersMap, optionMappings);

        model.addAttribute("attempt", attempt);
        model.addAttribute("exam", exam);
        model.addAttribute("questionsData", questionsData);
        model.addAttribute("answersData", answersData);
        model.addAttribute("remainingSeconds", remainingSeconds);
        model.addAttribute("warningCount", attempt.getWarningCount());
        model.addAttribute("maxWarnings", exam.getMaxWarnings());
        model.addAttribute("user", user);
        return "student/exam-window";
    }

    @PostMapping("/save-answer")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveAnswer(@RequestBody Map<String, Object> payload) {
        Long attemptId = toLong(payload.get("attemptId"));
        Long questionId = toLong(payload.get("questionId"));
        String selectedOption = payload.get("selectedOption") != null
                ? payload.get("selectedOption").toString() : null;
        boolean markedForReview = payload.get("markedForReview") != null
                && Boolean.parseBoolean(payload.get("markedForReview").toString());

        examService.saveAnswer(attemptId, questionId, selectedOption, markedForReview);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/submit/{attemptId}")
    public String submitExam(@PathVariable Long attemptId, RedirectAttributes redirectAttributes) {
        examService.submitAttempt(attemptId, false);
        redirectAttributes.addFlashAttribute("successMessage", "Exam submitted successfully");
        return "redirect:/exam/result/" + attemptId;
    }

    @PostMapping("/auto-submit/{attemptId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> autoSubmit(@PathVariable Long attemptId) {
        examService.submitAttempt(attemptId, true);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "redirectUrl", "/exam/result/" + attemptId
        ));
    }

    @PostMapping("/warning/{attemptId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> recordWarning(@PathVariable Long attemptId) {
        int warningCount = examService.incrementWarning(attemptId);
        ExamAttempt attempt = examService.getAttempt(attemptId);
        int maxWarnings = attempt.getExam().getMaxWarnings();

        Map<String, Object> response = new HashMap<>();
        response.put("warningCount", warningCount);
        response.put("maxWarnings", maxWarnings);
        response.put("shouldSubmit", warningCount >= maxWarnings);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/result/{attemptId}")
    public String result(@PathVariable Long attemptId, Model model) {
        User user = getCurrentUser();
        ExamAttempt attempt = examService.getAttempt(attemptId);

        // Verify ownership
        if (!attempt.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized access to exam result");
        }

        model.addAttribute("attempt", attempt);
        model.addAttribute("exam", attempt.getExam());
        model.addAttribute("user", user);
        return "student/result";
    }

    // ─────────────────────────────────────────────
    // JSON BUILDERS
    // ─────────────────────────────────────────────

    /**
     * Builds a JSON array string of questions in shuffled order with shuffled options.
     * Format: [{"id":1,"text":"...","options":[{"label":"A","text":"...","key":"C"},...]},...]
     */
    private String buildQuestionsJson(List<Long> questionOrder,
                                      Map<Long, Question> questionMap,
                                      Map<Long, String> optionMappings) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        for (Long qId : questionOrder) {
            Question q = questionMap.get(qId);
            if (q == null) continue;

            if (!first) sb.append(",");
            first = false;

            String mapping = optionMappings.getOrDefault(qId, "ABCD");
            List<Map<String, String>> options = examService.getShuffledOptions(q, mapping);

            sb.append("{");
            sb.append("\"id\":").append(q.getId()).append(",");
            sb.append("\"text\":").append(escapeJson(q.getQuestionText())).append(",");
            sb.append("\"marks\":").append(q.getMarks()).append(",");
            sb.append("\"options\":[");

            boolean firstOpt = true;
            for (Map<String, String> opt : options) {
                if (!firstOpt) sb.append(",");
                firstOpt = false;
                sb.append("{");
                sb.append("\"label\":").append(escapeJson(opt.get("label"))).append(",");
                sb.append("\"text\":").append(escapeJson(opt.get("text"))).append(",");
                sb.append("\"key\":").append(escapeJson(opt.get("key")));
                sb.append("}");
            }
            sb.append("]}");
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * Builds a JSON object mapping questionId → {selectedOption (DISPLAY label), markedForReview}.
     * Reverse-maps the stored original option back to the display label for the frontend.
     */
    private String buildAnswersJson(Map<Long, StudentAnswer> answersMap,
                                    Map<Long, String> optionMappings) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<Long, StudentAnswer> entry : answersMap.entrySet()) {
            Long qId = entry.getKey();
            StudentAnswer sa = entry.getValue();

            if (!first) sb.append(",");
            first = false;

            // Reverse map: original option → display label
            String displayOption = null;
            if (sa.getSelectedOption() != null && !sa.getSelectedOption().isBlank()) {
                String mapping = optionMappings.getOrDefault(qId, "ABCD");
                displayOption = examService.mapOriginalToDisplay(sa.getSelectedOption(), mapping);
            }

            sb.append("\"").append(qId).append("\":{");
            sb.append("\"selectedOption\":").append(displayOption != null ? escapeJson(displayOption) : "null").append(",");
            sb.append("\"markedForReview\":").append(sa.isMarkedForReview());
            sb.append("}");
        }

        sb.append("}");
        return sb.toString();
    }

    /** Escapes a string for safe JSON embedding. */
    private String escapeJson(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    /** Safely converts Object to Long. */
    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }
}
