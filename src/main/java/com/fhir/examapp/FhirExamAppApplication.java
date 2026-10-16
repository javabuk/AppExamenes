package com.fhir.examapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.util.Properties;

@SpringBootApplication
public class FhirExamAppApplication {

    public static void main(String[] args) {
        // Asegurar que la configuración local de la aplicación tenga prioridad
        System.setProperty("spring.config.name", "application");
        System.setProperty("spring.config.location", "optional:classpath:/,optional:classpath:/config/");
        System.setProperty("logging.config", "classpath:logback.xml");

        // Asegurar que el directorio de datos para SQLite existe
        File dataDir = new File("./data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        SpringApplication app = new SpringApplication(FhirExamAppApplication.class);
        
        // Garantizar que si el entorno del sistema tiene SPRING_CONFIG_LOCATION, use la configuración de la app
        Properties defaultProps = new Properties();
        defaultProps.put("spring.datasource.url", "jdbc:sqlite:./data/fhir_exam.db");
        defaultProps.put("spring.datasource.driver-class-name", "org.sqlite.JDBC");
        defaultProps.put("spring.jpa.database-platform", "org.hibernate.community.dialect.SQLiteDialect");
        defaultProps.put("spring.flyway.enabled", "true");
        defaultProps.put("spring.flyway.locations", "classpath:db/migration");
        defaultProps.put("spring.jackson.serialization.fail-on-empty-beans", "false");
        app.setDefaultProperties(defaultProps);

        app.run(args);
    }
}
