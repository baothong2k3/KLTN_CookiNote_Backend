package fit.kltn_cookinote_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class KltnCookiNoteBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(KltnCookiNoteBackendApplication.class, args);
    }

}
