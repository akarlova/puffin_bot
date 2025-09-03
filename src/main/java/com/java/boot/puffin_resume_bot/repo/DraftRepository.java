package com.java.boot.puffin_resume_bot.repo;

import com.java.boot.puffin_resume_bot.model.ResumeDraft;

import java.util.Optional;

public interface DraftRepository {
    Optional<ResumeDraft> get(Long userId);

    void save(ResumeDraft draft, long ttlSeconds);

    void delete(Long userId);

}
