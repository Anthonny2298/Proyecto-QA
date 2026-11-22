package com.corpomotriz.repuestos.seguridad.api;

import com.corpomotriz.repuestos.config.SecurityConfig;
import com.corpomotriz.repuestos.controller.AuthController;
import com.corpomotriz.repuestos.model.Usuario;
import com.corpomotriz.repuestos.repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * API POST /auth/login. Inicio de sesion (sin HU; restriccion de autenticacion del DAS 1.1).
 * Subcaracteristicas ISO/IEC 25010:2023: autenticidad y confidencialidad.
 */
@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("API /auth/login - seguridad")
class AuthControllerSeguridadTest {

    private static final String EMAIL = "ana@corpo.com";
    private static final String CLAVE = "Secreta1";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void sinUsuariosPorDefecto() {
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
    }

    private Usuario usuarioConClave(String claveAlmacenada) {
        return Usuario.builder().id(1L).nombre("Ana").email(EMAIL).contrasena(claveAlmacenada)
                .rol("CLIENTE").fechaCreacion(LocalDateTime.of(2025, 1, 1, 0, 0)).build();
    }

    private MockHttpServletRequestBuilder login(String email, String clave) {
        return post("/auth/login").contentType(APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"contrasena\":\"" + clave + "\"}");
    }

    @Test
    @DisplayName("UT-API-01 [AUT-3] Correo inexistente y clave incorrecta reciben el mismo mensaje")
    void mensajeGenericoAnteCredencialesInvalidas() throws Exception {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioConClave(CLAVE)));

        String correoInexistente = ApiTestSupport.respuesta(mvc, login("nadie@corpo.com", CLAVE)).getContentAsString();
        String claveIncorrecta = ApiTestSupport.respuesta(mvc, login(EMAIL, "Otra1234")).getContentAsString();

        String m1 = JsonPath.read(correoInexistente, "$.message");
        String m2 = JsonPath.read(claveIncorrecta, "$.message");
        assertThat(m1).as("El mensaje no debe revelar si el correo existe").isEqualTo(m2);
    }

    @Test
    @DisplayName("UT-API-02 [CON-5] La respuesta de login exitoso no incluye la contrasena")
    void loginNoDevuelveContrasena() throws Exception {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioConClave(CLAVE)));

        String body = ApiTestSupport.respuesta(mvc, login(EMAIL, CLAVE)).getContentAsString();

        assertThat(body).doesNotContain("contrasena").doesNotContain(CLAVE);
    }

    @Test
    @DisplayName("UT-API-03 [AUT-1] El login exitoso emite una credencial verificable (token o cookie de sesion)")
    void loginEmiteCredencial() throws Exception {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioConClave(CLAVE)));

        MockHttpServletResponse r = ApiTestSupport.respuesta(mvc, login(EMAIL, CLAVE));

        boolean emiteCredencial = r.getContentAsString().contains("\"token\"")
                || r.getHeader("Set-Cookie") != null
                || r.getHeader("Authorization") != null;
        assertThat(emiteCredencial).as("Sin credencial el backend no puede autenticar peticiones posteriores").isTrue();
    }

    @Test
    @DisplayName("UT-API-04 [INT-1] Login con correo mal formado responde 400")
    void loginCorreoMalFormado() throws Exception {
        assertThat(ApiTestSupport.status(mvc, login("no-es-correo", CLAVE))).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-05 [INT-1] Login sin contrasena responde 400")
    void loginSinContrasena() throws Exception {
        assertThat(ApiTestSupport.status(mvc, login(EMAIL, ""))).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-06 [CON-4] El login valida contra un hash bcrypt almacenado")
    void loginAceptaHashBcrypt() throws Exception {
        String hash = new BCryptPasswordEncoder().encode(CLAVE);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioConClave(hash)));

        assertThat(ApiTestSupport.status(mvc, login(EMAIL, CLAVE)))
                .as("Con contrasenas cifradas el usuario legitimo debe poder entrar").isEqualTo(200);
    }

    @Test
    @DisplayName("UT-API-07 [CON-4] Enviar el hash almacenado como contrasena no permite entrar")
    void loginRechazaPassTheHash() throws Exception {
        String hash = new BCryptPasswordEncoder().encode(CLAVE);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioConClave(hash)));

        assertThat(ApiTestSupport.status(mvc, login(EMAIL, hash))).isEqualTo(401);
    }

    @Test
    @DisplayName("UT-API-08 [CON-6] El error de login no expone traza ni excepcion interna")
    void loginErrorSinDetallesInternos() throws Exception {
        String body = ApiTestSupport.respuesta(mvc, login("nadie@corpo.com", CLAVE)).getContentAsString();

        assertThat(body).doesNotContain("trace").doesNotContain("exception").doesNotContain("java.");
    }
}
