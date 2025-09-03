package com.java.boot.puffin_resume_bot.tg;

import com.java.boot.puffin_resume_bot.model.*;
import com.java.boot.puffin_resume_bot.flow.ResumeState;
import com.java.boot.puffin_resume_bot.pdf.HtmlMaker;
import com.java.boot.puffin_resume_bot.service.ResumeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class ResumeBot extends TelegramLongPollingBot {

    private final String username;
    private final ResumeService resumeService;
    private static final int MAX_PHOTO_SIZE = 2_000_000;

    private static final List<String> TECH_SUGGEST = List.of(
            "Java Core", "Spring Boot", "Spring Framework", "Hibernate/JPA",
            "PostgreSQL", "REST API", "Docker", "Git", "Kafka");

    private static final List<String> SOFT_SUGGEST = List.of(
            "Коммуникабельность", "Командная работа", "Решение проблем", "Тайм-менеджмент",
            "Адаптивность", "Лидерство", "Ответственность", "Внимание к деталям");
			
	private static final List<String> TECH_PRESETS = List.of("Java","JS","HTML5","CSS3","Spring Boot",
		"PostgreSQL","Redis","Docker","JUnit","Maven");

    public ResumeBot(@Value("${bot.username}") String username,
                     @Value("${bot.token}") String token,
                     ResumeService resumeService) {
        super(token);
        this.username = username;
        this.resumeService = resumeService;

    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update == null || !update.hasMessage()) return;

        var msg = update.getMessage();
        String chatId = msg.getChatId().toString();
        Long userId = msg.getFrom().getId();

        //start
        if (msg.hasText() && "/start".equalsIgnoreCase(msg.getText().trim())) {
            resumeService.clear(userId);
            ResumeDraft draft = resumeService.getOrCreate(userId);
            //todo add lang
            send(chatId, "Пожалуйста, введи Имя и Фамилию.");
            return;
        }
        //draft
        ResumeDraft draft = resumeService.getOrCreate(userId);

        // FULL NAME
        if (draft.getState() == ResumeState.ASK_FULLNAME) {
            if (!msg.hasText()) {
                send(chatId, "Пожалуйста, напиши Имя и Фамилию");
                return;
            }
            String fullName = msg.getText().trim();
            draft.setFullName(fullName);
            draft.setState(ResumeState.ASK_PHOTO);
            resumeService.save(draft);
            send(chatId, "Загрузи фото jpg/png до 2 Мб.");
            return;
        }
        // PHOTO
        if (draft.getState() == ResumeState.ASK_PHOTO) {
            if (!msg.hasPhoto()) {
                send(chatId, "Пришли именно фотографию (jpg/png), до 2 МБ.");
                return;
            }
            var photo = msg.getPhoto().stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(null);
            if (photo == null) {
                send(chatId, "Не удалось прочитать фото. Пришли ещё раз.");
                return;
            }
            byte[] bytes = download(photo.getFileId());
            if (bytes.length > MAX_PHOTO_SIZE) {
                send(chatId, "Фото больше 2 МБ. Пришли поменьше, пожалуйста.");
                return;
            }
            draft.setPhotoBytes(bytes);
            draft.setState(ResumeState.ASK_PHONE);
            resumeService.save(draft);
            send(chatId, " Укажи номер телефона (пример: +1 570 498 1610 )");
            return;
        }

        //PHONE
        if (draft.getState() == ResumeState.ASK_PHONE) {
            if (!msg.hasText()) {
                send(chatId, "Напиши номер телефона. Еще образец: +39 431 489 3778");
            }
            String userNumber = msg.getText().trim();
            if (!userNumber.matches("^[+0-9()\\-\\s]{6,25}$")) {
                send(chatId, "Оставь только цифры, +, скобки, дефисы и пробелы.");
                return;
            }
            String countDigits = userNumber.replaceAll("\\D", "");
            if (countDigits.length() < 6 || countDigits.length() > 15) {
                send(chatId, "Проблемы с длиной номера. Введи 6–15 цифр.");
                return;
            }
            draft.setPhone(userNumber);
            draft.setState(ResumeState.ASK_EMAIL);
            resumeService.save(draft);
            send(chatId, "Укажи e-mail. Пример: myemail@gmail.com");
            return;
        }

        //E-MAIL
        if (draft.getState() == ResumeState.ASK_EMAIL) {
            if (!msg.hasText()) {
                send(chatId, "Укажи e-mail. Пример: yourmail@gmail.com");
            }
            String userEmail = msg.getText().trim();
            if (!userEmail.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                send(chatId, "Это не похоже на e-mail. Пример: name@mail.com");
                return;
            }
            draft.setEmail(userEmail);
            draft.setState(ResumeState.ASK_TELEGRAM);
            resumeService.save(draft);
            send(chatId, "Укажи телеграм (например, @yourhandle) или нажми 'Пропустить'", skipKb());
            return;
        }
        if (draft.getState() == ResumeState.ASK_TELEGRAM) {
            if (!msg.hasText()) {
                send(chatId, "Укажи телеграм (например, @yourhandle) или нажми 'Пропустить'", skipKb());
            }
            String userTg = msg.getText().trim();
            if (isSkip(userTg)) {
                draft.setTelegram(null);
            } else {
                String handledUserTg = userTg.replaceFirst("^(https?://)?(www\\.)?t\\.me/", "");
                if (handledUserTg.startsWith("@")) handledUserTg = handledUserTg.substring(1);
                if (!handledUserTg.matches("^[A-Za-z0-9_]{3,32}$")) {
                    send(chatId, "Не похоже на Telegram-ник. Пример: @yourhandle", skipKb());
                    return;
                }
                draft.setTelegram("@" + handledUserTg);
            }
            draft.setState(ResumeState.ASK_GITHUB);
            resumeService.save(draft);
            send(chatId, "Укажи свой гитхаб. Пример:  https://github.com/username", removeKb());
            return;
        }

        //GITHUB
        if (draft.getState() == ResumeState.ASK_GITHUB) {
            if (!msg.hasText()) {
                send(chatId, "Укажи github, это обязательное поле для it-специалиста (например,  https://github.com/username)");
                return;
            }
            String raw = msg.getText().trim();

            // 1) берем username из URL, если прислали ссылку
            String user = raw;
            var m = Pattern.compile("^(?:https?://)?(?:www\\.)?github\\.com/([A-Za-z0-9\\-]{1,100})/?$",
                            Pattern.CASE_INSENSITIVE)
                    .matcher(raw);
            if (m.matches()) user = m.group(1);

            // 2) нормализуем смахивающие на дефис символы к обычному '-'
            user = Normalizer.normalize(user, Normalizer.Form.NFKC)
                    .replaceAll("[\\u2010\\u2011\\u2012\\u2013\\u2014\\u2212]", "-");

            // 3) валидация по правилам GitHub (без двух дефисов подряд, не начинается/заканчивается '-')
            boolean ok = user.matches("(?i)^[a-z0-9](?:[a-z0-9]|-(?=[a-z0-9])){0,38}$");
            if (!ok) {
                send(chatId, "Не похоже на GitHub. Примеры: username или https://github.com/username");
                return;
            }

            String githubUrl = "https://github.com/" + user.toLowerCase();
            draft.setGithub(githubUrl);
            draft.setState(ResumeState.ASK_LINKEDIN);
            resumeService.save(draft);
            send(chatId, "Опционально, укажи LinkedIn (ссылка или username) или нажми «Пропустить».",
                    skipKb());
            return;
        }

        //LINKEDIN
        if (draft.getState() == ResumeState.ASK_LINKEDIN) {
            if (!msg.hasText()) {
                send(chatId, "Напиши LinkedIn или нажми «Пропустить».", skipKb());
                return;
            }

            String raw = msg.getText().trim();

            String url;

            if (isSkip(raw)) {
                url = null;
            } else if (raw.matches("^(https?://)?(www\\.)?linkedin\\.com/in/[A-Za-z0-9\\-_%]+/?$")) {
                url = raw.startsWith("http") ? raw : "https://" + raw.replaceFirst("^www\\.", "");
            } else if (raw.matches("^[A-Za-z0-9\\-_%]{3,100}$")) {
                url = "https://www.linkedin.com/in/" + raw;
            } else {
                send(chatId, "Не похоже на LinkedIn. Примеры: username или https://www.linkedin.com/in/username", skipKb());
                return;
            }

            draft.setLinkedin(url);
            draft.setState(ResumeState.ASK_POSITION);
            resumeService.save(draft);
            send(chatId, "Напиши должность, на которую претендуешь", removeKb());
            return;
        }
        //POSITION
        if(draft.getState() == ResumeState.ASK_POSITION) {
            if (!msg.hasText()) { send(chatId, "Напиши должность, на которую претендуешь");
                return; }

            String profession = msg.getText().trim();
            if (profession.length() < 3 || profession.length() > 60) {
                send(chatId, "Введи от 3 до 60 символов.");
                return;
            }

            draft.setPosition(profession);
            draft.setState(ResumeState.ASK_ABOUT);
            resumeService.save(draft);
            send(chatId, "Напиши коротко «О себе» (2–4 предложения, до ~800 символов).");
            return;
        }
        //ABOUT
        if(draft.getState() == ResumeState.ASK_ABOUT) {
            if (!msg.hasText()) { send(chatId, "Опиши себя: чем занимаешься, сильные стороны, ожидания.");
                return; }

            String about = msg.getText().trim().replaceAll("\\s+", " ");
            if (about.length() < 40 || about.length() > 800) {
                send(chatId, "Текст надо уложить в 40–800 символов");
                return;
            }

            draft.setAbout(about);
			draft.setProjectIndex(0);
			draft.setProjects(new ArrayList<>());
            draft.setState(ResumeState.ASK_PROJECT_URL);
            resumeService.save(draft);
			send(chatId, "Теперь разместим в резюме ссылки на 3 твоих лучших проекта. Проект 1/3 — пришли ссылку на проект на гитхабе или где он крутится в сети");
          
            return;
        }
		
		//PROJECTS: URL
		
		if(draft.getState() == ResumeState.ASK_PROJECT_URL) {
            if (!msg.hasText()) { send(chatId, "Пришли ссылку на проект пришли ссылку на проект на гитхабе или где он крутится в сети ");
			return; }
		  String raw = msg.getText().trim();

		  if (!raw.matches("^(https?://).+")) raw = "https://" + raw;
		  if (!raw.matches("^https?://[\\w.-]+\\.[A-Za-z]{2,}.*$")) {
			send(chatId, "Не похоже на ссылку. Пример: https://example.com"); 
			return;
		  }

		  int i = draft.getProjectIndex();
	
		  if (draft.getProjects().size() <= i) {
			  draft.getProjects().add(new ProjectItem());
			  }
			draft.getProjects().get(i).setUrl(raw);
			draft.getProjects().get(i).getTechs().clear();

        
            draft.setState(ResumeState.ASK_PROJECT_TECHS);
            resumeService.save(draft);
             send(chatId, "Выбери технологии для проекта " + (i+1) + ", можно несколько, но лучше до 4х штук. Нажми «Готово», когда закончишь.", techKb());
            return;
        }
		
		// PROJECTS: TECHS
		
	if(draft.getState() == ResumeState.ASK_PROJECT_TECHS) {
           if (!msg.hasText()) { send(chatId, "Жми кнопки технологий или «Свой вариант…». «Готово» — когда закончишь.", techKb());
		   return; }
		   
		String txt = msg.getText().trim();

		int i = draft.getProjectIndex();
		var p = draft.getProjects().get(i);
		var techs = p.getTechs();

	if (txt.equalsIgnoreCase("Готово")) {
    if (techs.isEmpty()) {
		send(chatId, "Добавь хотя бы одну технологию.", techKb()); 
		return; }
    // переходим к следующему проекту или дальше
		i++;
    if (i < 3) {
      draft.setProjectIndex(i);
      draft.setState(ResumeState.ASK_PROJECT_URL);
      resumeService.save(draft);
      send(chatId, "Проект " + (i+1) + "/3 — пришли ссылку ссылку на проект на гитхабе или где он крутится в сети");
      return;
    } else {
      // все 3 собраны 
      draft.setState(ResumeState.ASK_WORK_POSITION);
      resumeService.save(draft);
      send(chatId, "Заполняем опыт работы/стажировки. Укажи должность (например: Java Intern).");
      return;
    }
  }

  if (txt.equalsIgnoreCase("Свой вариант…")) {
    send(chatId, "Введи название технологии текстом (пример: Java, Node.js, .NET, C++).", techKb());
    return; 
  }

  String tech = txt;
  
  // проведем валидация кастомной технологии (буквы/цифры/пробел/+.#-/), чтобы не было откровенной ерунды
  if (!TECH_PRESETS.contains(tech)) {
    if (!tech.matches("^[A-Za-z0-9 .#+\\-/]{2,30}$")) {
      send(chatId, "Слишком странное название. Примеры: Spring Boot, Kotlin, C++", techKb());
      return;
    }
  }

  boolean exists = techs.stream().anyMatch(t -> t.equalsIgnoreCase(tech));
  if (!exists) techs.add(tech);

  resumeService.save(draft);
  send(chatId, "Технологии проекта " + (i+1) + ": " + String.join(", ", techs) + "\n" +
                "Добавь ещё или жми «Готово».", techKb());
  return;

        }
		
	//WORK EXPERIENCE: POSITION
	
	if(draft.getState() == ResumeState.ASK_WORK_POSITION) {
		
	if (!msg.hasText()) { 
	send(chatId, "Напиши должность текстом."); 
	return; }
  
		String position = msg.getText().trim();	
	  if (position.length() < 2 || position.length() > 80) {
		send(chatId, "Слишком коротко/длинно. Введи от 2 до 80 символов."); return;
	  }
	  
	  var w = new WorkItem();
	  w.setPosition(position);
	  draft.setPendingWork(w);
	  draft.setState(ResumeState.ASK_WORK_COMPANY);
	  resumeService.save(draft);
	  send(chatId, "Укажи компанию (например: JavaRush).");
	  return;
		
	
		
	}
	
	//WORK EXPERIENCE: COMPANY
	
	if(draft.getState() == ResumeState.ASK_WORK_COMPANY) {
		
		
	if (!msg.hasText()) { 
	send(chatId, "Напиши название компании текстом."); 
	return; }
	
	String company = msg.getText().trim();
	
	  if (company.length() < 2 || company.length() > 50) {
		send(chatId, "От 2 до 50 символов, пожалуйста."); 
		return;
	  }
	  var w = draft.getPendingWork();
	  if (w == null) { 
	  draft.setState(ResumeState.ASK_WORK_POSITION); 
	  resumeService.save(draft); 
	  send(chatId,"Сначала должность."); 
	  return; }
	  
	  w.setCompany(company);
	  draft.setPendingWork(w);
	  draft.setState(ResumeState.ASK_WORK_PERIOD);
	  resumeService.save(draft);
	  send(chatId, "Укажи период (например: Сентябрь 2024 — по наст. время).");
	  return;
				
	}
	
	//WORK EXPERIENCE: PERIOD
	
	if(draft.getState() == ResumeState.ASK_WORK_PERIOD) {
		
	if (!msg.hasText()) { 
	send(chatId, "Напиши период текстом (например: Июнь 2023 — Июль 2025)."); 
	return; }
	String period = msg.getText().trim();
	if (period.length() < 4 || period.length() > 60) {
    send(chatId, "Напиши период короче/яснее (4–60 символов).");
	return;
  }
	  var w = draft.getPendingWork();
	  if (w == null) { 
	  draft.setState(ResumeState.ASK_WORK_POSITION);
	  resumeService.save(draft); 
	  send(chatId,"Сначала должность."); 
	  return; 
	  }
	  w.setPeriod(period);
	  draft.setPendingWork(w);
	  draft.setState(ResumeState.ASK_WORK_DUTY);
	  resumeService.save(draft);
	  send(chatId, "Теперь сжато опиши, что надо было делать на работе/стажировке. Список будет состоять минимум из 2х, максимум из 5 обязанностей." +
              "Добавь обязанность №1. Когда закончишь — нажми «Готово».", doneKb());
		return;	
		
	 
	}
	
	//WORK EXPERIENCE: DUTIES
	
	if(draft.getState() == ResumeState.ASK_WORK_DUTY) {
		
	var w = draft.getPendingWork();
	if (w == null) { 
	draft.setState(ResumeState.ASK_WORK_POSITION); 
	resumeService.save(draft); 
	send(chatId,"Сначала должность."); 
	return; }

	if (!msg.hasText()) { 
	send(chatId, "Напиши обязанности или нажми «Готово».", doneKb()); 
	return; }
	
	String txt = msg.getText().trim();

	if (txt.equalsIgnoreCase("Готово")) {
    int n = w.getDuties() == null ? 0 : w.getDuties().size();
if (n < 2) { send(chatId, "Нужно минимум 2 обязанности. Добавь ещё одну.", doneKb()); return; }

// сохраняем место работы
if (draft.getWork() == null) draft.setWork(new java.util.ArrayList<>());
draft.getWork().add(w);
draft.setPendingWork(null);

int total = draft.getWork().size();
if (total < 3) {
  // предложить добавить ещё
  draft.setState(ResumeState.ASK_WORK_MORE);
  resumeService.save(draft);
  send(chatId, "Добавить ещё место работы или «Готово»?", moreWorkKb());
  return;
} else {
  // уже 3 места — идём дальше
  draft.setState(ResumeState.ASK_EDU_UNIVERSITY);
  resumeService.save(draft);
  send(chatId, "Заполняем образование (универ, курсы, колледж). Укажи место учебы (например: JavaRush University) или нажми «Пропустить».", skipKb());
  return;
}

  }

  // добавляем обязанность
  if (txt.length() < 3 || txt.length() > 140) {
    send(chatId, "Сделай формулировку на 3–140 символов."); 
	return;
  }
  if (w.getDuties().size() >= 5) {
    send(chatId, "Уже 5 пунктов. Нажми «Готово».", doneKb()); 
	return;
  }
  // валидация дублей
  boolean exists = w.getDuties().stream().anyMatch(d -> d.equalsIgnoreCase(txt));
  if (!exists) {w.getDuties().add(txt);}

  draft.setPendingWork(w);
  resumeService.save(draft);

  send(chatId, "Добавь ещё обязанность или нажми «Готово».\n" +
          "Сейчас: " + String.join("; ", w.getDuties()), doneKb());
  return;	
		
	}
			
	
// //WORK EXPERIENCE: MORE

if (draft.getState() == ResumeState.ASK_WORK_MORE) {
  if (!msg.hasText()) {
    send(chatId, "Выбери кнопку: «Добавить ещё место работы» или «Готово».", moreWorkKb());
    return;
  }
  String t = msg.getText().trim();

  if (t.equalsIgnoreCase("Добавить ещё место работы")) {
    draft.setPendingWork(null);
    draft.setState(ResumeState.ASK_WORK_POSITION);
    resumeService.save(draft);
    send(chatId, "Опыт работы — место " + (draft.getWork().size() + 1) + ". Укажи должность.");
    return;
  } else if (t.equalsIgnoreCase("Готово")) {
    draft.setState(ResumeState.ASK_EDU_UNIVERSITY);
  resumeService.save(draft);
  send(chatId, "Заполняем образование (универ, курсы, колледж). Укажи место учебы (например: JavaRush University) или нажми «Пропустить».", skipKb());
  return;
  } else {
    send(chatId, "Нажми одну из кнопок ниже.", moreWorkKb());
    return;
  }
}

 // EDUCATION: UNIVERSITY
 
 if (draft.getState() == ResumeState.ASK_EDU_UNIVERSITY) {
  if (!msg.hasText()) { 
  send(chatId, "Напиши название учебного заведения или «Пропустить».", skipKb());
  return; }
  String t = msg.getText().trim();

  if (isSkip(t)) { // пропускаем весь блок «Образование»
    draft.setState(ResumeState.ASK_LOCATION);
    resumeService.save(draft);
     send(chatId, "Укажи свою локацию, например: 'Китай, Шанхай'");
    return;
  }

  if (t.length() < 3 || t.length() > 80) {
    send(chatId, "От 3 до 80 символов, пожалуйста.");
    return;
  }
  var e = new EducationItem();
  e.setUniversity(t);
  draft.setPendingEducation(e);
  draft.setState(ResumeState.ASK_EDU_FACULTY);
  resumeService.save(draft);
  send(chatId, "Укажи факультет/специальность (например: Java Fullstack).");
  return;
}

// EDUCATION: FACULTY

 if (draft.getState() == ResumeState.ASK_EDU_FACULTY) {
  if (!msg.hasText()) { 
  send(chatId, "Напиши факультет/специальность (например: Физико-технический факультет)."); 
  return; }
  String t = msg.getText().trim();
  if (t.length() < 3 || t.length() > 80) {
    send(chatId, "От 3 до 80 символов, пожалуйста."); 
	return;
  }
  var e = draft.getPendingEducation();
  if (e == null) { 
  draft.setState(ResumeState.ASK_EDU_UNIVERSITY); 
  resumeService.save(draft); 
  send(chatId,"Сначала университет."); 
  return; }

  e.setFaculty(t);
  draft.setPendingEducation(e);
  draft.setState(ResumeState.ASK_EDU_PERIOD);
  resumeService.save(draft);
  send(chatId, "Укажи период обучения (например: Сентябрь 2024 — Июнь 2027).");
  return;
}

// EDUCATION: PERIOD

 if (draft.getState() == ResumeState.ASK_EDU_PERIOD) {
  if (!msg.hasText()) { 
  send(chatId, "Напиши период обучения текстом (например: Август 2025 — Сентябрь 2025)."); 
  return; }
  String t = msg.getText().trim();
  if (t.length() < 4 || t.length() > 60) {
    send(chatId, "Сделай период по шаблону: Сентябрь 2024 — Июнь 2027."); 
	return;
  }
  var e = draft.getPendingEducation();
  if (e == null) { 
  draft.setState(ResumeState.ASK_EDU_UNIVERSITY); 
  resumeService.save(draft); 
  send(chatId,"Сначала университет."); return; }

  e.setPeriod(t);
  if (draft.getEducation() == null){
	 draft.setEducation(new ArrayList<>()); 
  } 
  draft.getEducation().add(e);
  draft.setPendingEducation(null);
  resumeService.save(draft);

  // максимум 3
  if (draft.getEducation().size() >= 3) {
    draft.setState(ResumeState.ASK_LOCATION);
    resumeService.save(draft);
    send(chatId, "Выбрано 3 места учёбы. Переходим к местоположению.", removeKb());
    send(chatId, "Укажи свою локацию, например: 'Китай, Шанхай'");
    return;
  }

  draft.setState(ResumeState.ASK_EDU_MORE);
  resumeService.save(draft);
  send(chatId, "Добавить ещё место учебы или «Готово»?", moreEduKb());
  return;
}

//// EDUCATION: MORE

  if (draft.getState() == ResumeState.ASK_EDU_MORE) {
  if (!msg.hasText()) { 
  send(chatId, "Выбери кнопку: «Добавить ещё место учебы» или «Готово».", moreEduKb()); 
  return; }
  
  String t = msg.getText().trim();

  if (t.equalsIgnoreCase("Добавить ещё место учебы")) {
    int n = draft.getEducation() == null ? 0 : draft.getEducation().size();
    if (n >= 3) {
      draft.setState(ResumeState.ASK_LOCATION);
      resumeService.save(draft);
      send(chatId, "Уже 3 места. Переходим к языкам.", removeKb());
      send(chatId, "Укажи свою локацию, например: 'Китай, Шанхай'");
      return;
    }
    draft.setState(ResumeState.ASK_EDU_UNIVERSITY);
    resumeService.save(draft);
    send(chatId, "Образование — место " + (n + 1) + ". Укажи университет/курсы/другое или «Пропустить».", skipKb());
    return;
  } else if (t.equalsIgnoreCase("Готово")) {
    draft.setState(ResumeState.ASK_LOCATION);
    resumeService.save(draft);
    send(chatId, "Укажи свою локацию, например: 'Китай, Шанхай'");
    return;
  } else {
    send(chatId, "Ткни одну из кнопок ниже.", moreEduKb());
    return;
  }
}


        // LOCATION
        if (draft.getState() == ResumeState.ASK_LOCATION) {
            if (!msg.hasText()) {
                send(chatId, "Это обязательное поле для резюме. Укажи свою локацию, например: 'Китай, Шанхай'", removeKb());
                return;
            }

            String geo = msg.getText().trim();

            draft.setLocation(geo);
            draft.setState(ResumeState.CONFIRM_LOCATION);
            resumeService.save(draft);

            send(chatId, "Ты указал: " + geo + "\nВсё верно?", confirmKb());
            return;
        }

        //CONFIRM_LOCATION
        if (draft.getState() == ResumeState.CONFIRM_LOCATION) {
            if (!msg.hasText()) {
                send(chatId, "Ткни в кнопочку", confirmKb());
                return;
            }
            String ans = msg.getText().trim().toLowerCase();

            if (ans.equals("подтвердить")) {
                draft.setState(ResumeState.ASK_TECH_SKILLS);
                resumeService.save(draft);
                send(chatId, " Выбери из подсказок или перечисли свои Tech Skills через запятую: минимум 5 навыков, максимум - 9. " +
                        "Пример: Java, Spring Boot, PostgreSQL", skillsKb(TECH_SUGGEST));
                return;
            } else if (ans.equals("изменить")) {
                draft.setState(ResumeState.ASK_LOCATION);
                resumeService.save(draft);
                send(chatId, "Ок, укажи локацию ещё раз, например: 'Китай, Шанхай'", removeKb());
                return;
            } else {
                send(chatId, "Ткни в кнопочку", confirmKb());
                return;
            }
        }

        // TECH SKILLS

        if (draft.getState() == ResumeState.ASK_TECH_SKILLS) {
            if (draft.getTechSkills() == null) {
                draft.setTechSkills(new ArrayList<>());
            }

            if (msg.hasText()) {
                String text = msg.getText().trim();

                if (text.equalsIgnoreCase("Сбросить")) {
                    draft.setTechSkills(new ArrayList<>());
                    resumeService.save(draft);
                    send(chatId, "Список очищен. Выбери из подсказок или напиши свои навыки через запятую. Минимум 5 навыков, максимум - 9",
                            skillsKb(TECH_SUGGEST));
                    return;
                }

                if (text.equalsIgnoreCase("Готово")) {
                    int n = draft.getTechSkills().size();
                    if (n < 5) {
                        send(chatId, "Нужно минимум 5 навыков. Сейчас выбрано: " + n + ". Добавь ещё.",
                                skillsKb(TECH_SUGGEST));
                        return;
                    }
                    draft.setState(ResumeState.ASK_SOFT_SKILLS);
                    resumeService.save(draft);
                    send(chatId, "Теперь укажи Soft Skills (минимум 3 навыка, максимум 5). " +
                                    "Выбери из подсказок или напиши через запятую.",
                            skillsKb(SOFT_SUGGEST));
                    return;
                }
                // иначе — это либо кнопка-подсказка, либо пользовательский текст
                var incoming = parseSkills(text);
                var updated = addSkills(draft.getTechSkills(), incoming, 9);
                draft.setTechSkills(updated);
                resumeService.save(draft);
                String picked = String.join(", ", updated);
                int n = updated.size();
                if (n >= 9) {
                    draft.setState(ResumeState.ASK_SOFT_SKILLS);
                    resumeService.save(draft);
                    send(chatId, "Отлично! Выбрано 9/9: " + picked + "\nПереходим к Soft Skills.",
                            skillsKb(SOFT_SUGGEST));
                    return;
                } else {
                    send(chatId, "Выбрано (" + n + "/9): " + picked + "\n" +
                                    "Ещё можно добавить или нажать «Готово».",
                            skillsKb(TECH_SUGGEST));
                    return;
                }

            }
            // если пришло не текстовое сообщение
            send(chatId, "Тыкай кнопочки или напиши свои навыки через запятую.",
                    skillsKb(TECH_SUGGEST));
            return;
        }


        // 	SOFT_SKILLS
        if (draft.getState() == ResumeState.ASK_SOFT_SKILLS) {

            if (draft.getSoftSkills() == null) {
                draft.setSoftSkills(new ArrayList<>());
            }

            if (msg.hasText()) {
                String t = msg.getText().trim();

                if (t.equalsIgnoreCase("Сбросить")) {
                    draft.setSoftSkills(new ArrayList<>());
                    resumeService.save(draft);
                    send(chatId, "Список очищен. Выбери или напиши свои Soft Skills (минимум 3, максимум 5).",
                            skillsKb(SOFT_SUGGEST));
                    return;
                }

                if (t.equalsIgnoreCase("Готово")) {
                    int n = draft.getSoftSkills().size();
                    if (n < 3) {
                        send(chatId, "Нужно минимум 3 навыка. Сейчас выбрано: " + n + ". Добавь ещё.",
                                skillsKb(SOFT_SUGGEST));
                        return;
                    }

                    draft.setState(ResumeState.ASK_LANG_NAME);
                    resumeService.save(draft);
                    send(chatId, "Какими языками владеешь? Выбери минимум 2, максимум 5.\n" +
                            "После языка выберешь уровень.", langKb());
                    return;
                }

                var incoming = parseSkills(t);
                var updated = addSkills(draft.getSoftSkills(), incoming, 5);
                draft.setSoftSkills(updated);
                resumeService.save(draft);

                String picked = String.join(", ", updated);
                int n = updated.size();

                if (n >= 5) {
                    send(chatId, "Выбрано 5/5: " + picked + "\nНажми «Готово», чтобы перейти дальше.",
                            skillsKb(SOFT_SUGGEST));
                    return;
                } else {
                    send(chatId, "Выбрано (" + n + "/5): " + picked + "\n" +
                                    "Ещё можно добавить или нажать «Готово».",
                            skillsKb(SOFT_SUGGEST));
                    return;
                }
            }

            send(chatId, "Выбирай кнопками или напиши свои Soft Skills через запятую.",
                    skillsKb(SOFT_SUGGEST));
            return;
        }
        // LANGUAGES
        if (draft.getState() == ResumeState.ASK_LANG_NAME) {
            if (!msg.hasText()) {
                send(chatId, "Выбери язык из предложенных" +
                        " или укажи свой вариант", langKb());
                return;
            }
            String lang = msg.getText().trim();

            if (isAddMoreLang(lang)) {
                send(chatId, "Выбери язык.", langKb());
                return;
            }

            if (lang.equalsIgnoreCase("Готово")) {
                if (draft.getLanguages() == null || draft.getLanguages().size() < 2) {
                    send(chatId, "Нужно указать минимум 2 языка. Добавь ещё.", langKb());
                    return;
                }

                draft.setState(ResumeState.ASK_HOBBIES);
                resumeService.save(draft);
					  send(chatId, "Перечисли хобби через запятую, не больше 6. Или можешь «Пропустить».", skipKb());
					  return;
            }
            if (lang.equalsIgnoreCase("Другое…") || lang.equalsIgnoreCase("Другое")) {
                send(chatId, "Введи название языка текстом (например: Корейский).");
                return;
            }

            if (lang.length() < 2) {
                send(chatId, "Слишком коротко. Введи полноценное название языка.", langKb());
                return;
            }
            draft.setPendingLanguageName(lang);
            draft.setState(ResumeState.ASK_LANG_LEVEL);
            resumeService.save(draft);
            send(chatId, "Выбери уровень для «" + lang + "». " +
                    "Нажми кнопку уровня или введи 1–5 / A1–C2 / Native", levelKb());
            return;
        }
        if (draft.getState() == ResumeState.ASK_LANG_LEVEL) {
            if (!msg.hasText()) {
                send(chatId, "Нажми кнопку уровня или введи 1–5 / A1–C2 / Native/ Родной", levelKb());
                return;
            }
            String lvl = msg.getText().trim();

            String up = lvl.toUpperCase();
            Integer levelNum = null;
            String cefr = null;

            if (up.matches("[1-5]")) {
                levelNum = Integer.parseInt(up);
            } else if (Set.of("A1", "A2", "B1", "B2", "C1", "C2", "NATIVE", "РОДНОЙ","РОДНОЙ/NATIVE").contains(up)) {
                cefr = up.equals("РОДНОЙ/NATIVE") ? "NATIVE" : up;
            } else {
                send(chatId, "Это что за уровень? Используй 1–5 или A1–C2 / Native.", levelKb());
                return;
            }

            String name = draft.getPendingLanguageName();
            if (name == null || name.isBlank()) {
                draft.setState(ResumeState.ASK_LANG_NAME);
                resumeService.save(draft);
                send(chatId, "Сначала выбери язык.", langKb());
                return;
            }

            var item = new LanguageItem();
            item.setName(name);
            item.setLevel(levelNum);
            item.setCefr(cefr);

            if (draft.getLanguages() == null) {
                draft.setLanguages(new ArrayList<>());
            }
            boolean exists = draft.getLanguages().stream()
                    .anyMatch(li -> li.getName().equalsIgnoreCase(name));
            if (exists) {
                send(chatId, "«" + name + "» уже есть в списке. Выбери другой язык.", langKb());
                draft.setPendingLanguageName(null);
                draft.setState(ResumeState.ASK_LANG_NAME);
                resumeService.save(draft);
                return;
            }

            draft.getLanguages().add(item);
            draft.setPendingLanguageName(null);
            resumeService.save(draft);

            int count = draft.getLanguages().size();
            if (count < 2) {
                draft.setState(ResumeState.ASK_LANG_NAME);
                resumeService.save(draft);
                send(chatId, "Добавь ещё язык (минимум 2).", langKb());
                return;
            }
            if (count >= 5) {
                draft.setState(ResumeState.ASK_HOBBIES);
                resumeService.save(draft);
				  send(chatId, "Перечисли хобби через запятую, не больше 6." +
                          " Или можешь «Пропустить».", skipKb());
				  return;
            }

            draft.setState(ResumeState.ASK_LANG_NAME);
            resumeService.save(draft);
            send(chatId, "Добавить ещё язык или идем дальше(жми «Готово»?)", moreOrDoneKb());
            return;
        }
		
		// HOBBIES
		
 if (draft.getState() == ResumeState.ASK_HOBBIES) {
  if (!msg.hasText()) {
    send(chatId, "Перечисли хобби через запятую, не больше 6. Или можешь «Пропустить».", skipKb());
    return;
  }
  String raw = msg.getText().trim();

  // Пропуск всего блока «Хобби»
  if (isSkip(raw)) {
    draft.setHobbies(new ArrayList<>()); 
    draft.setState(ResumeState.READY);
   resumeService.save(draft);
                send(chatId, "Формирую резюме…", removeKb());
                byte[] html = HtmlMaker.makeRu(draft, true);
                sendFile(chatId, html, "resume.html");
                resumeService.clear(userId);
                send(chatId, "Резюме готово! /start — создать еще одно.");
                return;

  }

  String[] parts = raw.split(",");
  LinkedHashSet<String> set = new LinkedHashSet<>(); // чтобы сохранить порядок+избавиться от  дублей!!!
  for (String p : parts) {
    String h = p.trim().replaceAll("\\s+", " ");
    if (h.isEmpty()) continue;
    if (h.length() < 2 || h.length() > 40) continue;
    set.add(h);
  }

  if (set.isEmpty()) {
    send(chatId, "Хобби не рспознано. Пример: Путешествия, Алгоритмы, Корейская культура", skipKb());
    return;
  }
  if (set.size() > 6) {
    send(chatId, "Многовато. Оставь не больше 6 хобби.", skipKb());
    return;
  }

  // ок
  draft.setHobbies(new ArrayList<>(set));
  draft.setState(ResumeState.READY);
  resumeService.save(draft);

  send(chatId, "Формирую резюме…", removeKb());
                byte[] html = HtmlMaker.makeRu(draft, true);
                sendFile(chatId, html, "resume.html");
                resumeService.clear(userId);
                send(chatId, "Резюме готово! /start — создать еще одно.");
  return;
}


    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdatesReceived(List<Update> updates) {
        super.onUpdatesReceived(updates);
    }

   @Override
    public void onRegister() {
        super.onRegister();
    }




    // хелперы и клавиатурки
	
    private void send(String chatId, String text, ReplyKeyboard markup) {
        try {
            var sendMessage =
                    SendMessage.builder()
                            .chatId(chatId)
                            .text(text)
                            .build();
            sendMessage.setReplyMarkup(markup);
            execute(sendMessage);
        } catch (Exception ignored) {
        }
    }

    private void send(String chatId, String text) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .build());
        } catch (Exception ignored) {
        }
    }

    private void sendFile(String chatId, byte[] data, String filename) {
        try {
            var doc = new SendDocument();
            doc.setChatId(chatId);
            doc.setDocument(new InputFile(
                    new ByteArrayInputStream(data), filename));
            execute(doc);
        } catch (Exception ignored) {
        }
    }

    private byte[] download(String field) {
        try {
            var tgFile = execute(new GetFile(field));
            var dlF = downloadFile(tgFile);
            return Files.readAllBytes(dlF.toPath());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось скачать файл Telegram", e);
        }
    }

    private ReplyKeyboardMarkup confirmKb() {
        var row = new KeyboardRow();
        row.add("Подтвердить");
        row.add("Изменить");
        var kb = new ReplyKeyboardMarkup(List.of(row));
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(true);
        return kb;
    }


    private ReplyKeyboardMarkup skipKb() {
        var row = new KeyboardRow();
        row.add("Пропустить");
        var rows = List.of(row);
        var kb = new ReplyKeyboardMarkup(rows);
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(true);
        return kb;
    }

    private ReplyKeyboardRemove removeKb() {
        return new ReplyKeyboardRemove(true);
    }

    private boolean isSkip(String t) {
        if (t == null) return false;
        t = t.trim().toLowerCase();
        return t.equals("пропустить") || t.equals("skip") || t.equals("-");
    }


    private ReplyKeyboardMarkup skillsKb(List<String> suggest) {
        var rows = new ArrayList<KeyboardRow>();

        // разложим подсказки по 2-3 на ряд

        for (int i = 0; i < suggest.size(); i += 3) {
            var row = new KeyboardRow();
            row.add(suggest.get(i));
            if (i + 1 < suggest.size()) row.add(suggest.get(i + 1));
            if (i + 2 < suggest.size()) row.add(suggest.get(i + 2));
            rows.add(row);
        }
        // строка управления
        var control = new KeyboardRow();
        control.add("Готово");
        control.add("Сбросить");
        rows.add(control);

        var kb = new ReplyKeyboardMarkup(rows);
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(false);
        return kb;
    }

    // Регистронезависимость
    private List<String> parseSkills(String text) {
        return Arrays.stream(text.split("[,;\\n]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.replaceAll("\\s{2,}", " "))
                .map(s -> s.length() > 40 ? s.substring(0, 40) : s)
                .distinct()
                .toList();
    }

    private List<String> addSkills(List<String> current, List<String> incoming, int max) {
        var out = new ArrayList<>(current == null ? List.of() : current);
        for (var s : incoming) {
            boolean exists = out.stream().anyMatch(x -> x.equalsIgnoreCase(s));
            if (!exists && out.size() < max) {
                out.add(s);
            }
        }
        return out;
    }

    private ReplyKeyboardMarkup langKb() {
        var r1 = new KeyboardRow();
        r1.addAll(List.of("Русский", "Английский", "Китайский"));
        var r2 = new KeyboardRow();
        r2.addAll(List.of("Арабский", "Французский", "Испанский"));
        var r3 = new KeyboardRow();
        r3.addAll(List.of("Другое…"));
        var kb = new ReplyKeyboardMarkup(List.of(r1, r2, r3));
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(false);
        return kb;
    }

    private ReplyKeyboardMarkup levelKb() {
        var r1 = new KeyboardRow();
        r1.addAll(List.of("1", "2", "3", "4", "5"));
        var r2 = new KeyboardRow();
        r2.addAll(List.of("A1", "A2", "B1", "B2", "C1", "C2"));
        var r3 = new KeyboardRow();
        r3.addAll(List.of("Родной/Native"));
        var kb = new ReplyKeyboardMarkup(List.of(r1, r2, r3));
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(true);
        return kb;
    }

    private ReplyKeyboardMarkup moreOrDoneKb() {
        var r = new KeyboardRow();
        r.addAll(List.of("Добавить ещё язык", "Готово"));
        var kb = new ReplyKeyboardMarkup(List.of(r));
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(true);
        return kb;
    }
    private boolean isAddMoreLang(String t) {
        if (t == null) return false;
        String s = t.trim().toLowerCase(java.util.Locale.ROOT).replace('ё','е');
        return s.equals("добавить еще язык") || s.equals("добавить язык");
    }

	private ReplyKeyboardMarkup techKb() {
	  var rows = new ArrayList<KeyboardRow>();
	  var row = new KeyboardRow();
	  for (int i = 0; i < TECH_PRESETS.size(); i++) {
		row.add(TECH_PRESETS.get(i));
		if ((i+1) % 3 == 0) { rows.add(row); 
		row = new KeyboardRow(); }
	  }
	  if (!row.isEmpty()) rows.add(row);
	  var r2 = new KeyboardRow(); 
	  r2.addAll(List.of("Свой вариант…","Готово"));
	  rows.add(r2);
	  var kb = new ReplyKeyboardMarkup(rows);
	  kb.setResizeKeyboard(true); 
	  kb.setOneTimeKeyboard(false);
	  return kb;
	}
	
	private ReplyKeyboardMarkup doneKb() {
	  var row = new KeyboardRow();
	  row.add("Готово");
	  var kb  = new ReplyKeyboardMarkup(List.of(row));
	  kb.setResizeKeyboard(true); 
	  kb.setOneTimeKeyboard(true);
	  return kb;
}

	private ReplyKeyboardMarkup moreWorkKb() {
	  var row = new KeyboardRow();
	  row.addAll(List.of("Добавить ещё место работы", "Готово"));
	  var kb = new ReplyKeyboardMarkup(List.of(row));
	  kb.setResizeKeyboard(true);
	  kb.setOneTimeKeyboard(true);
	  return kb;
}

	private ReplyKeyboardMarkup moreEduKb() {
	  var row = new KeyboardRow();
	  row.addAll(List.of("Добавить ещё место учебы", "Готово"));
	  var kb = new ReplyKeyboardMarkup(List.of(row));
	  kb.setResizeKeyboard(true); 
	  kb.setOneTimeKeyboard(true);
	  return kb;
}


 
}
