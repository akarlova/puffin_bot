package com.java.boot.puffin_resume_bot.pdf;

import com.java.boot.puffin_resume_bot.model.ResumeDraft;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class HtmlMaker {
    private static final TemplateEngine engine = createEngine();

    private static TemplateEngine createEngine() {
        var r = new ClassLoaderTemplateResolver();
        r.setPrefix("templates/");
        r.setSuffix(".html");
        r.setCharacterEncoding("UTF-8");
        r.setTemplateMode("HTML");
        var e = new TemplateEngine();
        e.setTemplateResolver(r);
        return e;
    }

    private static String loadText(String path) {
        try (InputStream is = HtmlMaker.class.getResourceAsStream(path)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось загрузить данные: " + path, e);
        }
    }

    public static byte[] makeRu(ResumeDraft draft, boolean greenTheme) {
        Context context = new Context();
        context.setVariable("fullName", draft.getFullName());

        String photoDataUrl = null;
        if (draft.getPhotoBytes() != null) {
            photoDataUrl = "data:image/jpeg;base64," +
                    Base64.getEncoder().encodeToString(draft.getPhotoBytes());
        }
        context.setVariable("photoDataUrl", photoDataUrl);

        String css = loadText(greenTheme ? "/templates/css/styles-green.css"
                : "/templates/css/styles-blue.css");
        context.setVariable("inlineCss", css);
        context.setVariable("phone", draft.getPhone());
        context.setVariable("email", draft.getEmail());
        context.setVariable("telegram", draft.getTelegram());
        context.setVariable("github", draft.getGithub());
        context.setVariable("linkedin", draft.getLinkedin());
		context.setVariable("geo", draft.getLocation().trim());
		
		context.setVariable("techSkills", draft.getTechSkills());
		context.setVariable("softSkills", draft.getSoftSkills());

        context.setVariable("languages", draft.getLanguages());

        context.setVariable("position", draft.getPosition());
        context.setVariable("about", draft.getAbout());
		
		context.setVariable("projects", draft.getProjects());
		context.setVariable("work", draft.getWork());
		context.setVariable("education", draft.getEducation());
		context.setVariable("hobbies", draft.getHobbies());

		


        String html = engine.process("resume-ru-export", context);
        return html.getBytes(StandardCharsets.UTF_8);
    }
}

