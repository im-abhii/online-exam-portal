package com.examportal.service;

import com.examportal.entity.*;
import com.examportal.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class ExamService {

    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final StudentAnswerRepository studentAnswerRepository;

    public ExamService(ExamRepository examRepository,
                       QuestionRepository questionRepository,
                       ExamAttemptRepository examAttemptRepository,
                       StudentAnswerRepository studentAnswerRepository) {
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.examAttemptRepository = examAttemptRepository;
        this.studentAnswerRepository = studentAnswerRepository;
    }

    // ─────────────────────────────────────────────
    // EXAM CRUD
    // ─────────────────────────────────────────────

    public Exam saveExam(Exam exam) {
        return examRepository.save(exam);
    }

    @Transactional(readOnly = true)
    public Exam findExamById(Long id) {
        return examRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exam not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Exam> findAllExams() {
        return examRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Exam> findAvailableExams() {
        LocalDateTime now = LocalDateTime.now();
        return examRepository.findByPublishedTrueAndStartTimeBeforeAndEndTimeAfter(now, now);
    }

    public void deleteExam(Long id) {
        examRepository.deleteById(id);
        log.info("Exam deleted: {}", id);
    }

    public void togglePublish(Long id) {
        Exam exam = findExamById(id);
        exam.setPublished(!exam.isPublished());
        examRepository.save(exam);
        log.info("Exam {} publish toggled to: {}", id, exam.isPublished());
    }

    // ─────────────────────────────────────────────
    // QUESTION CRUD
    // ─────────────────────────────────────────────

    public Question saveQuestion(Question question) {
        return questionRepository.save(question);
    }

    @Transactional(readOnly = true)
    public Question findQuestionById(Long id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Question> findQuestionsByExamId(Long examId) {
        return questionRepository.findByExamId(examId);
    }

    public void deleteQuestion(Long id) {
        questionRepository.deleteById(id);
        log.info("Question deleted: {}", id);
    }

    @Transactional(readOnly = true)
    public int countQuestionsByExamId(Long examId) {
        return questionRepository.countByExamId(examId);
    }

    // ─────────────────────────────────────────────
    // ATTEMPT MANAGEMENT
    // ─────────────────────────────────────────────

    public ExamAttempt startAttempt(Long userId, Long examId) {
        // Resume existing in-progress attempt
        Optional<ExamAttempt> existing = examAttemptRepository
                .findByUserIdAndExamIdAndStatus(userId, examId, "IN_PROGRESS");
        if (existing.isPresent()) {
            return existing.get();
        }

        // Block re-attempts
        if (examAttemptRepository.existsByUserIdAndExamId(userId, examId)) {
            throw new RuntimeException("You have already attempted this exam");
        }

        Exam exam = findExamById(examId);
        List<Question> questions = findQuestionsByExamId(examId);

        if (questions.isEmpty()) {
            throw new RuntimeException("This exam has no questions");
        }

        // Shuffle question order
        List<Long> questionIds = questions.stream()
                .map(Question::getId)
                .collect(Collectors.toList());
        Collections.shuffle(questionIds);
        String questionOrder = questionIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        // Generate random option mappings per question
        StringBuilder mappingsBuilder = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            if (i > 0) mappingsBuilder.append(";");
            mappingsBuilder.append(questions.get(i).getId())
                           .append(":")
                           .append(generateOptionMapping());
        }

        ExamAttempt attempt = ExamAttempt.builder()
                .user(User.builder().id(userId).build())
                .exam(exam)
                .startTime(LocalDateTime.now())
                .totalQuestions(questions.size())
                .totalMarks(questions.stream().mapToInt(Question::getMarks).sum())
                .questionOrder(questionOrder)
                .optionMappings(mappingsBuilder.toString())
                .status("IN_PROGRESS")
                .build();

        ExamAttempt saved = examAttemptRepository.save(attempt);
        log.info("Exam attempt started - user: {}, exam: {}, attempt: {}", userId, examId, saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public ExamAttempt getAttempt(Long attemptId) {
        return examAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new RuntimeException("Attempt not found with id: " + attemptId));
    }

    public void saveAnswer(Long attemptId, Long questionId, String selectedDisplayOption, boolean markedForReview) {
        // Find or create the answer record
        StudentAnswer answer = studentAnswerRepository
                .findByAttemptIdAndQuestionId(attemptId, questionId)
                .orElseGet(() -> {
                    ExamAttempt attempt = getAttempt(attemptId);
                    Question question = findQuestionById(questionId);
                    return StudentAnswer.builder()
                            .attempt(attempt)
                            .question(question)
                            .build();
                });

        if (selectedDisplayOption != null && !selectedDisplayOption.isBlank()) {
            // Map display option back to the original option using the attempt's mapping
            ExamAttempt attempt = getAttempt(attemptId);
            Map<Long, String> mappings = parseOptionMappings(attempt.getOptionMappings());
            String mapping = mappings.get(questionId);

            if (mapping != null) {
                String originalOption = mapDisplayToOriginal(selectedDisplayOption.trim(), mapping);
                answer.setSelectedOption(originalOption);
            } else {
                answer.setSelectedOption(selectedDisplayOption.trim());
            }
        } else {
            // Clear the response
            answer.setSelectedOption(null);
        }

        answer.setMarkedForReview(markedForReview);
        answer.setAnsweredAt(LocalDateTime.now());
        studentAnswerRepository.save(answer);
    }

    public ExamAttempt submitAttempt(Long attemptId, boolean autoSubmitted) {
        ExamAttempt attempt = getAttempt(attemptId);
        attempt.setEndTime(LocalDateTime.now());
        attempt.setAutoSubmitted(autoSubmitted);

        List<StudentAnswer> answers = studentAnswerRepository.findByAttemptId(attemptId);
        List<Question> questions = findQuestionsByExamId(attempt.getExam().getId());

        int attempted = 0;
        int correct = 0;
        int wrong = 0;
        int score = 0;
        int totalMarks = questions.stream().mapToInt(Question::getMarks).sum();

        for (StudentAnswer sa : answers) {
            if (sa.getSelectedOption() != null && !sa.getSelectedOption().isBlank()) {
                attempted++;
                Question q = sa.getQuestion();
                if (sa.getSelectedOption().equalsIgnoreCase(q.getCorrectOption())) {
                    correct++;
                    score += q.getMarks();
                } else {
                    wrong++;
                }
            }
        }

        attempt.setAttempted(attempted);
        attempt.setCorrectAnswers(correct);
        attempt.setWrongAnswers(wrong);
        attempt.setScore(score);
        attempt.setTotalMarks(totalMarks);
        attempt.setPercentage(totalMarks > 0 ? (score * 100.0 / totalMarks) : 0.0);
        attempt.setPassed(attempt.getPercentage() >= attempt.getExam().getPassingPercentage());
        attempt.setStatus(autoSubmitted ? "AUTO_SUBMITTED" : "COMPLETED");

        ExamAttempt saved = examAttemptRepository.save(attempt);
        log.info("Exam submitted - attempt: {}, score: {}/{}, percentage: {}%",
                attemptId, score, totalMarks, String.format("%.1f", attempt.getPercentage()));
        return saved;
    }

    public int incrementWarning(Long attemptId) {
        ExamAttempt attempt = getAttempt(attemptId);
        attempt.setWarningCount(attempt.getWarningCount() + 1);
        examAttemptRepository.save(attempt);
        log.info("Warning incremented for attempt {}: count={}", attemptId, attempt.getWarningCount());
        return attempt.getWarningCount();
    }

    @Transactional(readOnly = true)
    public Map<Long, StudentAnswer> getAnswersMap(Long attemptId) {
        List<StudentAnswer> answers = studentAnswerRepository.findByAttemptId(attemptId);
        return answers.stream()
                .collect(Collectors.toMap(
                        sa -> sa.getQuestion().getId(),
                        sa -> sa,
                        (a, b) -> b
                ));
    }

    @Transactional(readOnly = true)
    public boolean hasAttempted(Long userId, Long examId) {
        return examAttemptRepository.existsByUserIdAndExamId(userId, examId);
    }

    @Transactional(readOnly = true)
    public List<ExamAttempt> getStudentAttempts(Long userId) {
        return examAttemptRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public double getStudentAverageScore(Long userId) {
        List<ExamAttempt> attempts = examAttemptRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return attempts.stream()
                .filter(a -> !"IN_PROGRESS".equals(a.getStatus()))
                .mapToDouble(ExamAttempt::getPercentage)
                .average()
                .orElse(0.0);
    }

    @Transactional(readOnly = true)
    public List<ExamAttempt> getAllCompletedAttempts() {
        return examAttemptRepository.findByStatusNot("IN_PROGRESS");
    }

    @Transactional(readOnly = true)
    public Double getAverageScore() {
        Double avg = examAttemptRepository.findAveragePercentage();
        return avg != null ? avg : 0.0;
    }

    @Transactional(readOnly = true)
    public double getPassRate() {
        long completed = examAttemptRepository.countCompleted();
        if (completed == 0) return 0.0;
        long passed = examAttemptRepository.countPassed();
        return passed * 100.0 / completed;
    }

    @Transactional(readOnly = true)
    public long getCompletedCount() {
        return examAttemptRepository.countCompleted();
    }

    // ─────────────────────────────────────────────
    // OPTION SHUFFLING & MAPPING HELPERS
    // ─────────────────────────────────────────────

    /**
     * Generates a random permutation of "ABCD" (e.g. "CBDA").
     * The mapping means: display position A shows the option at mapping.charAt(0),
     * display position B shows mapping.charAt(1), etc.
     */
    private String generateOptionMapping() {
        List<Character> chars = new ArrayList<>(Arrays.asList('A', 'B', 'C', 'D'));
        Collections.shuffle(chars);
        StringBuilder sb = new StringBuilder();
        for (char c : chars) sb.append(c);
        return sb.toString();
    }

    /**
     * Maps a display option label back to the original option letter.
     * If mapping="CBDA" and displayOption="A", then A→index 0→mapping.charAt(0)='C' (original).
     */
    private String mapDisplayToOriginal(String displayOption, String mapping) {
        int index = displayOption.charAt(0) - 'A';
        if (index >= 0 && index < mapping.length()) {
            return String.valueOf(mapping.charAt(index));
        }
        return displayOption;
    }

    /**
     * Reverse maps an original option letter to the display label.
     * If mapping="CBDA" and originalOption='C', find index of 'C' in mapping → 0 → display='A'.
     */
    public String mapOriginalToDisplay(String originalOption, String mapping) {
        if (originalOption == null || mapping == null) return null;
        int index = mapping.indexOf(originalOption.charAt(0));
        if (index >= 0) {
            return String.valueOf((char) ('A' + index));
        }
        return originalOption;
    }

    /**
     * Returns shuffled options for a question based on the mapping string.
     * Each entry has keys: label (display A/B/C/D), text (option content), key (original letter).
     */
    public List<Map<String, String>> getShuffledOptions(Question q, String mapping) {
        List<Map<String, String>> options = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            char originalLetter = mapping.charAt(i);
            String displayLabel = String.valueOf((char) ('A' + i));
            String text = getOptionText(q, originalLetter);

            Map<String, String> option = new LinkedHashMap<>();
            option.put("label", displayLabel);
            option.put("text", text);
            option.put("key", String.valueOf(originalLetter));
            options.add(option);
        }
        return options;
    }

    private String getOptionText(Question q, char option) {
        return switch (option) {
            case 'A' -> q.getOptionA();
            case 'B' -> q.getOptionB();
            case 'C' -> q.getOptionC();
            case 'D' -> q.getOptionD();
            default -> "";
        };
    }

    /**
     * Parses question order string "5,12,3,8" into a list of Long IDs.
     */
    public List<Long> parseQuestionOrder(String orderStr) {
        if (orderStr == null || orderStr.isBlank()) return Collections.emptyList();
        return Arrays.stream(orderStr.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    /**
     * Parses option mappings string "5:CBDA;12:DCAB;3:ABDC" into a map of questionId→mapping.
     */
    public Map<Long, String> parseOptionMappings(String mappingsStr) {
        Map<Long, String> map = new HashMap<>();
        if (mappingsStr == null || mappingsStr.isBlank()) return map;
        for (String entry : mappingsStr.split(";")) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                map.put(Long.parseLong(parts[0].trim()), parts[1].trim());
            }
        }
        return map;
    }
}
