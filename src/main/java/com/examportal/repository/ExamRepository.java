package com.examportal.repository;

import com.examportal.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findByPublishedTrueOrderByStartTimeDesc();

    List<Exam> findByPublishedTrueAndStartTimeBeforeAndEndTimeAfter(LocalDateTime now1, LocalDateTime now2);

    List<Exam> findAllByOrderByCreatedAtDesc();
}
