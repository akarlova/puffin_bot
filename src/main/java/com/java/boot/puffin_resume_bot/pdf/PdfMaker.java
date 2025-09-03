package com.java.boot.puffin_resume_bot.pdf;

import com.java.boot.puffin_resume_bot.model.ResumeDraft;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class PdfMaker {
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
    public static byte[] makeRu(ResumeDraft draft) {
         var context = new Context();
         context.setVariable("fullName", draft.getFullName());
         String photoDataUrl = null;
         if(draft.getPhotoBytes() != null) {
             photoDataUrl = "data:image/jpeg;base64," + Base64.getEncoder().
                     encodeToString(draft.getPhotoBytes());
         }
         context.setVariable("photoDataUrl", photoDataUrl);

         String html = engine.process("resume-ru", context);
          try (var out = new ByteArrayOutputStream()) {
              String baseUrl = PdfMaker.class.getResource("/templates/").toExternalForm();

              var builder = new PdfRendererBuilder();
              builder.useFastMode();
              builder.withHtmlContent(html, baseUrl);
              builder.toStream(out);
              builder.run();

              return out.toByteArray();
          } catch(Exception e) {
              throw new RuntimeException("Не удалось создать PDF", e);
          }
    }
}
