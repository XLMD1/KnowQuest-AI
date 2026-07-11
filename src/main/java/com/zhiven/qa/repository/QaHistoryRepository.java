package com.zhiven.qa.repository;

import com.zhiven.qa.entity.QaHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QaHistoryRepository extends JpaRepository<QaHistory, Long> {
    List<QaHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
