package com.corpomotriz.repuestos.seguridad.config;

import com.corpomotriz.repuestos.config.SecurityConfig;
import com.corpomotriz.repuestos.controller.AuthController;
import com.corpomotriz.repuestos.controller.PagoController;
import com.corpomotriz.repuestos.controller.ProductoController;
import com.corpomotriz.repuestos.controller.ProveedorController;
import com.corpomotriz.repuestos.controller.UsuarioController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.io.IOException;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuracion y construccion: application.properties, logback, pom.xml y anotaciones de seguridad.
 * Se ejecuta desde la carpeta backend (directorio base de Maven).
 */
@DisplayName("Configuracion y construccion - seguridad")
class ConfiguracionSeguridadTest {

    private static final List<Class<?>> CONTROLADORES = List.of(AuthController.class, PagoController.class,
            ProductoController.class, ProveedorController.class, UsuarioController.class);

    private static Properties props;

    @BeforeAll
    static void leerPropiedades() throws IOException {
        props = new Properties();
        try (Reader r = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) {
            props.load(r);
        }
    }

    @Test
    @DisplayName("UT-CFG-01 [CON-3] La contrasena de la base de datos no esta escrita en el repositorio")
    void contrasenaBdNoLiteral() {
        String valor = props.getProperty("spring.datasource.password", "");

        assertThat(valor).as("Debe venir de una variable de entorno: ${DB_PASSWORD}").startsWith("${");
    }

    @Test
    @DisplayName("UT-CFG-02 [CON-3] La aplicacion no se conecta con el usuario root de MySQL")
    void usuarioBdNoRoot() {
        assertThat(props.getProperty("spring.datasource.username", "")).isNotEqualToIgnoringCase("root");
    }

    @Test
    @DisplayName("UT-CFG-03 [CON-7] El log de Spring Security no esta en nivel DEBUG")
    void logSeguridadNoDebug() {
        assertThat(props.getProperty("logging.level.org.springframework.security", "INFO"))
                .isNotIn("DEBUG", "TRACE", "debug", "trace");
    }

    @Test
    @DisplayName("UT-CFG-04 [CON-7] show-sql esta desactivado")
    void showSqlDesactivado() {
        assertThat(props.getProperty("spring.jpa.show-sql", "false")).isEqualToIgnoringCase("false");
    }

    @Test
    @DisplayName("UT-CFG-05 [INT-5] Hibernate no modifica el esquema en ejecucion (ddl-auto distinto de update)")
    void ddlAutoNoUpdate() {
        assertThat(props.getProperty("spring.jpa.hibernate.ddl-auto", "none")).isIn("none", "validate");
    }

    @Test
    @DisplayName("UT-CFG-06 [RST-1] Si el pom declara jjwt, el codigo lo usa (sin dependencias muertas)")
    void jjwtDeclaradoSeUsa() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        if (!pom.contains("jjwt")) {
            return;
        }
        boolean usado;
        try (Stream<Path> fuentes = Files.walk(Path.of("src/main/java"))) {
            usado = fuentes.filter(p -> p.toString().endsWith(".java")).anyMatch(p -> {
                try {
                    return Files.readString(p).contains("io.jsonwebtoken");
                } catch (IOException e) {
                    return false;
                }
            });
        }
        assertThat(usado).as("jjwt declarado en pom.xml pero ninguna clase lo importa").isTrue();
    }

    @Test
    @DisplayName("UT-CFG-07 [RST-3] Ningun controlador usa @CrossOrigin abierto a cualquier origen")
    void crossOriginRestringido() {
        List<String> abiertos = new ArrayList<>();
        for (Class<?> c : CONTROLADORES) {
            CrossOrigin co = c.getAnnotation(CrossOrigin.class);
            if (co != null && co.origins().length == 0 && co.value().length == 0) {
                abiertos.add(c.getSimpleName());
            }
        }
        assertThat(abiertos).as("Controladores con @CrossOrigin sin origins").isEmpty();
    }

    @Test
    @DisplayName("UT-CFG-08 [CON-4] La configuracion de seguridad declara un PasswordEncoder")
    void existePasswordEncoder() {
        boolean declarado = Arrays.stream(SecurityConfig.class.getDeclaredMethods())
                .anyMatch(m -> PasswordEncoder.class.isAssignableFrom(m.getReturnType()));

        assertThat(declarado).isTrue();
    }

    @Test
    @DisplayName("UT-CFG-09 [CON-6] Existe un manejador global de excepciones (@ControllerAdvice)")
    void existeManejadorGlobal() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ControllerAdvice.class));

        long encontrados = scanner.findCandidateComponents("com.corpomotriz.repuestos").stream()
                .filter(bd -> !bd.getBeanClassName().contains(".seguridad.")).count();

        assertThat(encontrados).isGreaterThan(0);
    }

    @Test
    @DisplayName("UT-CFG-10 [CON-2] Cada operacion de escritura declara su autorizacion por rol")
    void escriturasConAutorizacion() {
        Set<String> anotacionesAutorizacion = Set.of("PreAuthorize", "Secured", "RolesAllowed");
        List<String> sinAutorizacion = new ArrayList<>();
        for (Class<?> c : CONTROLADORES) {
            for (Method m : c.getDeclaredMethods()) {
                boolean escritura = m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(PutMapping.class)
                        || m.isAnnotationPresent(DeleteMapping.class);
                boolean autorizada = Arrays.stream(m.getAnnotations()).map(Annotation::annotationType)
                        .map(Class::getSimpleName).anyMatch(anotacionesAutorizacion::contains);
                if (escritura && !autorizada && c != AuthController.class) {
                    sinAutorizacion.add(c.getSimpleName() + "." + m.getName());
                }
            }
        }
        assertThat(sinAutorizacion).as("Metodos POST/PUT/DELETE sin @PreAuthorize ni @Secured").isEmpty();
    }

    @Test
    @DisplayName("UT-CFG-11 [CON-7] El log raiz de logback no esta en DEBUG")
    void logbackRaizNoDebug() throws IOException {
        String logback = Files.readString(Path.of("src/main/resources/logback-spring.xml")).toLowerCase();

        assertThat(logback).doesNotContain("<root level=\"debug\"").doesNotContain("<root level=\"trace\"");
    }
}
