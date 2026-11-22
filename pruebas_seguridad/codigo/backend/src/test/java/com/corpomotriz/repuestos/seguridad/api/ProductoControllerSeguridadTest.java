package com.corpomotriz.repuestos.seguridad.api;

import com.corpomotriz.repuestos.config.SecurityConfig;
import com.corpomotriz.repuestos.controller.ProductoController;
import com.corpomotriz.repuestos.service.ProductoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * API /productos. HU Insertar Producto (ERS 1.1.2), Consultar, Modificar y Eliminar Producto (Brief v1.2).
 * Incluye las pruebas transversales de seguridad web (CORS y cabeceras), ejecutadas sobre este controlador.
 */
@WebMvcTest(controllers = ProductoController.class)
@Import(SecurityConfig.class)
@DisplayName("API /productos y seguridad web - seguridad")
class ProductoControllerSeguridadTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductoService productoService;

    private static String productoJson(String nombre, String precio, String cantidad) {
        return "{\"nombre\":\"" + nombre + "\",\"descripcion\":\"Filtro de aceite\",\"precio\":" + precio
                + ",\"cantidad\":" + cantidad + ",\"proveedorId\":1,\"creadoPorId\":1,\"marca\":\"Bosch\"}";
    }

    @Test
    @DisplayName("UT-API-34 [CON-1] Insertar producto sin autenticacion responde 401 o 403")
    void insertarProductoRequiereAutenticacion() throws Exception {
        int status = ApiTestSupport.status(mvc, post("/productos").contentType(APPLICATION_JSON)
                .content(productoJson("Filtro", "25.50", "10")));

        assertThat(status).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-35 [INT-1] Insertar producto con precio 0 responde 400 (RN-112-3)")
    void insertarProductoPrecioCero() throws Exception {
        int status = ApiTestSupport.status(mvc, post("/productos").contentType(APPLICATION_JSON)
                .content(productoJson("Filtro", "0", "10")));

        assertThat(status).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-36 [INT-1] Insertar producto con precio negativo responde 400")
    void insertarProductoPrecioNegativo() throws Exception {
        int status = ApiTestSupport.status(mvc, post("/productos").contentType(APPLICATION_JSON)
                .content(productoJson("Filtro", "-10", "10")));

        assertThat(status).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-37 [INT-1] Insertar producto sin nombre responde 400")
    void insertarProductoSinNombre() throws Exception {
        int status = ApiTestSupport.status(mvc, post("/productos").contentType(APPLICATION_JSON)
                .content(productoJson("", "25.50", "10")));

        assertThat(status).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-38 [INT-1] Modificar producto con cantidad negativa responde 400")
    void modificarProductoCantidadNegativa() throws Exception {
        int status = ApiTestSupport.status(mvc, put("/productos/1").contentType(APPLICATION_JSON)
                .content(productoJson("Filtro", "25.50", "-1")));

        assertThat(status).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-39 [CON-1] Eliminar producto sin autenticacion responde 401 o 403")
    void eliminarProductoRequiereAutenticacion() throws Exception {
        assertThat(ApiTestSupport.status(mvc, delete("/productos/1"))).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-40 [Control] Consultar el catalogo es publico para visitantes (Brief: Visitantes)")
    void catalogoPublico() throws Exception {
        assertThat(ApiTestSupport.status(mvc, get("/productos"))).isEqualTo(200);
    }

    @Test
    @DisplayName("UT-API-41 [RST-3] CORS no autoriza peticiones desde un origen desconocido")
    void corsRechazaOrigenDesconocido() throws Exception {
        MockHttpServletResponse r = ApiTestSupport.respuesta(mvc, options("/productos")
                .header("Origin", "https://atacante.example")
                .header("Access-Control-Request-Method", "POST"));

        String permitido = r == null ? null : r.getHeader("Access-Control-Allow-Origin");
        assertThat(permitido).as("Ningun origen arbitrario debe quedar autorizado").isNull();
    }

    @Test
    @DisplayName("UT-API-42 [RST-3] Las respuestas incluyen X-Content-Type-Options: nosniff")
    void cabeceraNoSniff() throws Exception {
        MockHttpServletResponse r = ApiTestSupport.respuesta(mvc, get("/productos"));

        assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("UT-API-43 [RST-3] Las respuestas incluyen X-Frame-Options contra clickjacking")
    void cabeceraXFrameOptions() throws Exception {
        MockHttpServletResponse r = ApiTestSupport.respuesta(mvc, get("/productos"));

        assertThat(r.getHeader("X-Frame-Options")).isIn("DENY", "SAMEORIGIN");
    }

    @Test
    @DisplayName("UT-API-44 [CON-7] Las respuestas no se guardan en cache (Cache-Control no-store)")
    void cabeceraCacheControl() throws Exception {
        MockHttpServletResponse r = ApiTestSupport.respuesta(mvc, get("/productos"));

        assertThat(r.getHeader("Cache-Control")).contains("no-store");
    }
}
