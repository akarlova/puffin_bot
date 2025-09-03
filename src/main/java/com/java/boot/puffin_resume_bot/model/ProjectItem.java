package com.java.boot.puffin_resume_bot.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProjectItem {
		private String Url;
		private List<String> techs = new ArrayList<>();
}