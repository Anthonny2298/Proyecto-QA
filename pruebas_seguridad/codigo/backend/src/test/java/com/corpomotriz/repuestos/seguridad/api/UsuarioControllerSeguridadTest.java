package com.corpomotriz.repuestos.seguridad.api;

import com.corpomotriz.repuestos.config.SecurityConfig;
import com.corpomotriz.repuestos.controller.UsuarioController;
import com.corpomotriz.repuestos.dto.UsuarioDTO;
import com.corpomotriz.repuestos.service.UsuarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * API /usuarios. HU Crear Perfil (ERS 1.1.3), Examinar Perfil, Editar y Eliminar Perfil (Brief v1.2).
 */
@WebMvcTest(controllers = UsuarioController.class)
@Import(SecurityConfig.class)
@DisplayName("API /usuarios - seguridad")
class UsuarioControllerSeguridadTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UsuarioService usuarioService;

    private static String usuarioJson(String email, String clave, String rol) {
        return "{\"nombre\":\"Ana Perez\",\"email\":\"" + email + "\",\"contrasena\":\"" + clave
                + "\",\"rol\":\"" + rol + "\"}";
    }

    @Test
    @DisplayName("UT-API-09 [INT-4] Un visitante no puede registrarse con rol ADMINISTRADOR")
    void registroNoAceptaRolAdministrador() throws Exception {
        int status = ApiTestSupport.status(mvc, post("/usuarios").contentType(APPLICATION_JSON)
                .content(usuarioJson("ana@corpo.com", "Secreta1", "ADMINISTRADOR")));

        assertThat(status).as("El rol no debe ser un dato que decide el cliente").isIn(400, 403);
    }

    @Test
    @DisplayName("UT-API-10 [CON-1] Listar usuarios sin autenticacion responde 401 o 403")
    void listarUsuariosRequiereAutenticacion() throws Exception {
        assertThat(ApiTestSupport.status(mvc, get("/usuarios"))).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-11 [CON-1] Consultar un usuario por id sin autenticacion responde 401 o 403")
    void consultarUsuarioRequiereAutenticacion() throws Exception {
        assertThat(ApiTestSupport.status(mvc, get("/usuarios/1"))).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-12 [CON-1] Modificar un usuario sin autenticacion responde 401 o 403")
    void modificarUsuarioRequiereAutenticacion() throws Exception {
        int status = ApiTestSupport.status(mvc, put("/usuarios/1").contentType(APPLICATION_JSON)
                .content(usuarioJson("ana@corpo.com", "Secreta1", "CLIENTE")));

        assertThat(status).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-13 [CON-1] Eliminar un usuario sin autenticacion responde 401 o 403")
    void eliminarUsuarioRequiereAutenticacion() throws Exception {
        assertThat(ApiTestSupport.status(mvc, delete("/usuarios/1"))).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-14 [CON-6] Un correo duplicado al modificar responde 409, no 404")
    void correoDuplicadoNoSeInformaComoNoEncontrado() throws Exception {
        when(usuarioService.updateUser(eq(1), any())).thenThrow(new RuntimeException("El email ya está registrado"));

        int status = ApiTestSupport.status(mvc, put("/usuarios/1").contentType(APPLICATION_JSON)
                .content(usuarioJson("otro@corpo.com", "Secreta1", "CLIENTE")));

        assertThat(status).isEqualTo(409);
    }

    @Test
    @DisplayName("UT-API-15 [INT-1] Registro con contrasena de menos de 6 caracteres responde 400")
    void registroContrasenaCorta() throws Exception {
        int status = ApiTestSupport.status(mvc, post("/usuarios").contentType(APPLICATION_JSON)
                .content(usuarioJson("ana@corpo.com", "12345", "CLIENTE")));

        assertThat(status).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-16 [INT-1] Registro con correo mal formado responde 400")
    void registroCorreoInvalido() throws Exception {
        int status = ApiTestSupport.status(mvc, post("/usuarios").contentType(APPLICATION_JSON)
                .content(usuarioJson("malo", "Secreta1", "CLIENTE")));

        assertThat(status).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-17 [CON-5] El listado de usuarios no expone contrasenas")
    void listadoSinContrasenas() throws Exception {
        when(usuarioService.getAllUsuarios()).thenReturn(List.of(
                UsuarioDTO.builder().id(1).nombre("Ana").email("ana@corpo.com").rol("CLIENTE").build()));

        String body = ApiTestSupport.respuesta(mvc, get("/usuarios")).getContentAsString();

        assertThat(body).doesNotContain("contrasena");
    }
}
