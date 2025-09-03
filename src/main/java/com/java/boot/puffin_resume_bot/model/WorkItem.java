
package com.java.boot.puffin_resume_bot.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class WorkItem {
  private String position;       
  private String company;       
  private String period;        
  private List<String> duties = new ArrayList<>(); 
}