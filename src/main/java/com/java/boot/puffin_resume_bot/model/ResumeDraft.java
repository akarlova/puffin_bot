package com.java.boot.puffin_resume_bot.model;

import com.java.boot.puffin_resume_bot.flow.ResumeState;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ResumeDraft {

    private Long userId;
    private String uiLang = "ru";
    private String resumeLang = "ru";
    private ResumeState state = ResumeState.ASK_FULLNAME;

    private byte[] photoBytes;
    private String fullName;
    private String phone;
    private String email;
    private String telegram;
    private String github;
    private String linkedin;
	private String location;
	
	private List<String> techSkills;
	private List<String> softSkills;

    private List<LanguageItem> languages = new ArrayList<>();
    private String pendingLanguageName;

    private String position;
    private String about;
	
	private List<ProjectItem> projects;
	private int projectIndex = 0;
	
	List<WorkItem> work = new ArrayList<>();
	WorkItem pendingWork;
	
	List<EducationItem> education = new ArrayList<>();
	EducationItem pendingEducation;
	
	private List<String> hobbies = new ArrayList<>();


    private Instant updatedAt = Instant.now();

}
