package com.examportal.controller;

import com.examportal.entity.Exam;
import com.examportal.entity.ExamAttempt;
import com.examportal.entity.Question;
import com.examportal.entity.User;
import com.examportal.service.ExamService;
import com.examportal.service.UserService;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.awt.Color;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    private final UserService userService;
    private final ExamService examService;

    public AdminController(UserService userService, ExamService examService) {
        this.userService = userService;
        this.examService = examService;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ─────────────────────────────────────────────
    // DASHBOARD
    // ─────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("user", getCurrentUser());
        model.addAttribute("totalStudents", userService.countStudents());
        model.addAttribute("totalExams", examService.findAllExams().size());
        model.addAttribute("completedAttempts", examService.getCompletedCount());
        model.addAttribute("averageScore", String.format("%.1f", examService.getAverageScore()));
        model.addAttribute("passRate", String.format("%.1f", examService.getPassRate()));
        return "admin/dashboard";
    }

    // ─────────────────────────────────────────────
    // EXAM MANAGEMENT
    // ─────────────────────────────────────────────

    @GetMapping("/exams")
    public String exams(Model model) {
        List<Exam> exams = examService.findAllExams();

        // Attach question count to each exam for display
        for (Exam exam : exams) {
            int count = examService.countQuestionsByExamId(exam.getId());
            exam.setTotalMarks(count); // reuse totalMarks temporarily for display
        }

        model.addAttribute("user", getCurrentUser());
        model.addAttribute("exams", exams);
        return "admin/exams";
    }

    @GetMapping("/exam/new")
    public String newExam(Model model) {
        model.addAttribute("exam", new Exam());
        model.addAttribute("editing", false);
        return "admin/exam-form";
    }

    @GetMapping("/exam/edit/{id}")
    public String editExam(@PathVariable Long id, Model model) {
        model.addAttribute("exam", examService.findExamById(id));
        model.addAttribute("editing", true);
        return "admin/exam-form";
    }

    @PostMapping("/exam/save")
    public String saveExam(@ModelAttribute Exam exam, RedirectAttributes redirectAttributes) {
        // Preserve createdBy for new exams
        if (exam.getId() == null) {
            exam.setCreatedBy(getCurrentUser());
        } else {
            // Keep existing createdBy on updates
            Exam existing = examService.findExamById(exam.getId());
            exam.setCreatedBy(existing.getCreatedBy());
        }

        examService.saveExam(exam);
        redirectAttributes.addFlashAttribute("successMessage", "Exam saved successfully");
        return "redirect:/admin/exams";
    }

    @PostMapping("/exam/delete/{id}")
    public String deleteExam(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        examService.deleteExam(id);
        redirectAttributes.addFlashAttribute("successMessage", "Exam deleted successfully");
        return "redirect:/admin/exams";
    }

    @PostMapping("/exam/toggle-publish/{id}")
    public String togglePublish(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        examService.togglePublish(id);
        redirectAttributes.addFlashAttribute("successMessage", "Exam publish status updated");
        return "redirect:/admin/exams";
    }

    // ─────────────────────────────────────────────
    // QUESTION MANAGEMENT
    // ─────────────────────────────────────────────

    @GetMapping("/exam/{examId}/questions")
    public String questions(@PathVariable Long examId, Model model) {
        model.addAttribute("exam", examService.findExamById(examId));
        model.addAttribute("questions", examService.findQuestionsByExamId(examId));
        return "admin/questions";
    }

    @GetMapping("/exam/{examId}/question/new")
    public String newQuestion(@PathVariable Long examId, Model model) {
        model.addAttribute("question", new Question());
        model.addAttribute("examId", examId);
        model.addAttribute("editing", false);
        return "admin/question-form";
    }

    @GetMapping("/exam/{examId}/question/edit/{id}")
    public String editQuestion(@PathVariable Long examId, @PathVariable Long id, Model model) {
        model.addAttribute("question", examService.findQuestionById(id));
        model.addAttribute("examId", examId);
        model.addAttribute("editing", true);
        return "admin/question-form";
    }

    @PostMapping("/exam/{examId}/question/save")
    public String saveQuestion(@PathVariable Long examId,
                               @ModelAttribute Question question,
                               RedirectAttributes redirectAttributes) {
        Exam exam = examService.findExamById(examId);
        question.setExam(exam);
        examService.saveQuestion(question);
        redirectAttributes.addFlashAttribute("successMessage", "Question saved successfully");
        return "redirect:/admin/exam/" + examId + "/questions";
    }

    @PostMapping("/question/delete/{id}")
    public String deleteQuestion(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Question question = examService.findQuestionById(id);
        Long examId = question.getExam().getId();
        examService.deleteQuestion(id);
        redirectAttributes.addFlashAttribute("successMessage", "Question deleted successfully");
        return "redirect:/admin/exam/" + examId + "/questions";
    }

    // ─────────────────────────────────────────────
    // STUDENT MANAGEMENT
    // ─────────────────────────────────────────────

    @GetMapping("/students")
    public String students(@RequestParam(required = false) String search, Model model) {
        List<User> students;
        if (search != null && !search.isBlank()) {
            students = userService.searchStudents(search);
            model.addAttribute("search", search);
        } else {
            students = userService.findAllStudents();
        }
        model.addAttribute("user", getCurrentUser());
        model.addAttribute("students", students);
        return "admin/students";
    }

    @PostMapping("/student/toggle/{id}")
    public String toggleStudent(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.toggleUserEnabled(id);
        redirectAttributes.addFlashAttribute("successMessage", "Student status updated");
        return "redirect:/admin/students";
    }

    // ─────────────────────────────────────────────
    // RESULTS
    // ─────────────────────────────────────────────

    @GetMapping("/results")
    public String results(Model model) {
        model.addAttribute("user", getCurrentUser());
        model.addAttribute("attempts", examService.getAllCompletedAttempts());
        return "admin/results";
    }

    // ─────────────────────────────────────────────
    // EXPORT - CSV
    // ─────────────────────────────────────────────

    @GetMapping("/export/csv")
    public void exportCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=results.csv");

        List<ExamAttempt> attempts = examService.getAllCompletedAttempts();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        PrintWriter writer = response.getWriter();
        writer.println("Student Name,Email,Exam Title,Score,Total Marks,Percentage,Status,Date");

        for (ExamAttempt attempt : attempts) {
            String name = escapeCsv(attempt.getUser().getFirstName() + " " + attempt.getUser().getLastName());
            String email = escapeCsv(attempt.getUser().getEmail());
            String examTitle = escapeCsv(attempt.getExam().getTitle());
            String date = attempt.getCreatedAt() != null ? attempt.getCreatedAt().format(dtf) : "";

            writer.printf("%s,%s,%s,%d,%d,%.1f,%s,%s%n",
                    name, email, examTitle,
                    attempt.getScore(), attempt.getTotalMarks(),
                    attempt.getPercentage(), attempt.getStatus(), date);
        }

        writer.flush();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ─────────────────────────────────────────────
    // EXPORT - PDF
    // ─────────────────────────────────────────────

    @GetMapping("/export/pdf")
    public void exportPdf(HttpServletResponse response) throws IOException, DocumentException {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=results.pdf");

        List<ExamAttempt> attempts = examService.getAllCompletedAttempts();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        Document document = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        // Title
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(44, 62, 80));
        Paragraph title = new Paragraph("Exam Results Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        // Table with 8 columns
        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3f, 4f, 4f, 1.5f, 1.5f, 2f, 2f, 3f});

        // Header styling
        Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
        Color headerBg = new Color(52, 73, 94);
        String[] headers = {"Student Name", "Email", "Exam Title", "Score", "Total", "Percentage", "Status", "Date"};

        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setPadding(8);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        // Data rows
        Font dataFont = new Font(Font.HELVETICA, 9);
        Color altRowColor = new Color(236, 240, 241);
        int rowNum = 0;

        for (ExamAttempt attempt : attempts) {
            Color rowColor = (rowNum % 2 == 0) ? Color.WHITE : altRowColor;

            addCell(table, attempt.getUser().getFirstName() + " " + attempt.getUser().getLastName(), dataFont, rowColor);
            addCell(table, attempt.getUser().getEmail(), dataFont, rowColor);
            addCell(table, attempt.getExam().getTitle(), dataFont, rowColor);
            addCell(table, String.valueOf(attempt.getScore()), dataFont, rowColor);
            addCell(table, String.valueOf(attempt.getTotalMarks()), dataFont, rowColor);
            addCell(table, String.format("%.1f%%", attempt.getPercentage()), dataFont, rowColor);
            addCell(table, attempt.getStatus(), dataFont, rowColor);
            addCell(table, attempt.getCreatedAt() != null ? attempt.getCreatedAt().format(dtf) : "", dataFont, rowColor);

            rowNum++;
        }

        document.add(table);
        document.close();
    }

    private void addCell(PdfPTable table, String text, Font font, Color bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(6);
        table.addCell(cell);
    }
}
