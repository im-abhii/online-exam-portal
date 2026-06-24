package com.examportal.repository;

import com.examportal.entity.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    List<ExamAttempt> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ExamAttempt> findByExamIdOrderByPercentageDesc(Long examId);

    Optional<ExamAttempt> findByUserIdAndExamIdAndStatus(Long userId, Long examId, String status);

    boolean existsByUserIdAndExamId(Long userId, Long examId);

    @Query("SELECT AVG(ea.percentage) FROM ExamAttempt ea WHERE ea.status <> 'IN_PROGRESS'")
    Double findAveragePercentage();

    @Query("SELECT COUNT(ea) FROM ExamAttempt ea WHERE ea.passed = true AND ea.status <> 'IN_PROGRESS'")
    long countPassed();

    @Query("SELECT COUNT(ea) FROM ExamAttempt ea WHERE ea.status <> 'IN_PROGRESS'")
    long countCompleted();

    List<ExamAttempt> findByStatusNotOrderByPercentageDesc(String status);

    List<ExamAttempt> findByStatusNot(String status);
}
