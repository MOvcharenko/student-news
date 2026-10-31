package com.example.studentnews.repository;

import com.example.studentnews.entity.NewsItem;
import com.example.studentnews.entity.NewsStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsRepository extends JpaRepository<NewsItem, Long> {
    List<NewsItem> findAllByStatusOrderByUpdatedAtDesc(NewsStatus status);
}
