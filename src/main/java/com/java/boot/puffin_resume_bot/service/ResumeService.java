package com.java.boot.puffin_resume_bot.service;

import com.java.boot.puffin_resume_bot.model.ResumeDraft;
import com.java.boot.puffin_resume_bot.repo.DraftRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ResumeService {
    public static final long TTL_SECONDS = 24 * 3600; // 24 hours

    private final DraftRepository drafts;

    public ResumeService(DraftRepository drafts) {
        this.drafts = drafts;
    }

    public ResumeDraft getOrCreate(Long userId) {
        return drafts.get(userId).orElseGet(() -> {
            var d = new ResumeDraft();
            d.setUserId(userId);
            save(d);
            return d;
        });
    }

    public void save(ResumeDraft draft) {
        draft.setUpdatedAt(Instant.now());
        drafts.save(draft, TTL_SECONDS);
    }

    public void clear(Long userId) {
        drafts.delete(userId);
    }
}
