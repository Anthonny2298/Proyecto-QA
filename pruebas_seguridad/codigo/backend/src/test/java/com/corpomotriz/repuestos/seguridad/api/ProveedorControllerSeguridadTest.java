package com.corpomotriz.repuestos.seguridad.api;

import com.corpomotriz.repuestos.config.SecurityConfig;
import com.corpomotriz.repuestos.controller.ProveedorController;
import com.corpomotriz.repuestos.service.ProveedorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * API /proveedores. HU Anadir Proveedores (ERS 1.1.1, alcance: solo Administrador), Consultar,
 * Modificar y Eliminar Proveedores (Brief v1.2).
 */
@WebMvcTest(controllers = ProveedorController.class)
@Import(SecurityConfig.class)
@DisplayName("API /proveedores - seguridad")
class ProveedorControllerSeguridadTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProveedorService proveedorService;

    private static String proveedorJson(String email) {
        return "{\"nombre\":\"Bosch Venezuela\",\"telefono\":\"+58 212 5550000\",\"email\":\"" + email
                + "\",\"localizacion\":\"Caracas\",\"especializacion\":\"Componentes electricos\",\"plazoEntrega\":\"7 dias\"}";
    }

    @Test
    @DisplayName("UT-API-45 [CON-1] Anadir proveedor sin autenticacion responde 401 o 403")
    void anadirProveedorRequiereAutenticacion() throws Exception {
        int status = ApiTestSupport.status(mvc, post("/proveedores").contentType(APPLICATION_JSON)
                .content(proveedorJson("ventas@bosch.com")));

        assertThat(status).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-46 [CON-1] Consultar proveedores sin autenticacion responde 401 o 403")
    void consultarProveedoresRequiereAutenticacion() throws Exception {
        assertThat(ApiTestSupport.status(mvc, get("/proveedores"))).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-47 [CON-1] Eliminar proveedor sin autenticacion responde 401 o 403")
    void eliminarProveedorRequiereAutenticacion() throws Exception {
        assertThat(ApiTestSupport.status(mvc, delete("/proveedores/1"))).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-48 [INT-1] Anadir proveedor con correo mal formado responde 400")
    void anadirProveedorCorreoInvalido() throws Exception {
        int status = ApiTestSupport.status(mvc, post("/proveedores").contentType(APPLICATION_JSON)
                .content(proveedorJson("no-es-correo")));

        assertThat(status).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-49 [INT-1] Un id fuera del rango entero responde 400, no 500")
    void idFueraDeRango() throws Exception {
        assertThat(ApiTestSupport.status(mvc, get("/proveedores/3000000000")))
                .as("Math.toIntExact no debe terminar en error interno").isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-50 [CON-1] Modificar proveedor sin autenticacion responde 401 o 403")
    void modificarProveedorRequiereAutenticacion() throws Exception {
        int status = ApiTestSupport.status(mvc, put("/proveedores/1").contentType(APPLICATION_JSON)
                .content(proveedorJson("ventas@bosch.com")));

        assertThat(status).isIn(401, 403);
    }
}
