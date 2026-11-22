package com.corpomotriz.repuestos.seguridad.api;

import com.corpomotriz.repuestos.config.SecurityConfig;
import com.corpomotriz.repuestos.controller.PagoController;
import com.corpomotriz.repuestos.dto.response.PagoResponseDTO;
import com.corpomotriz.repuestos.service.PagoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * API /pagos. HU Registrar Pago (ERS 1.1.4) y Modificar, Consultar y Eliminar Pago (Brief v1.2).
 */
@WebMvcTest(controllers = PagoController.class)
@Import(SecurityConfig.class)
@DisplayName("API /pagos - seguridad")
class PagoControllerSeguridadTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PagoService pagoService;

    private static String pagoJson(String monto, String estado, String metodo, String cantidad,
                                   String titular, String cvv, String email) {
        return "{\"emailUsuario\":\"" + email + "\",\"monto\":" + monto + ",\"metodo\":\"" + metodo
                + "\",\"estado\":\"" + estado + "\",\"productos\":[{\"productoId\":1,\"cantidad\":" + cantidad
                + "}],\"numeroTarjeta\":\"4111111111111111\",\"fechaExpiracion\":\"12/30\",\"cvv\":\"" + cvv
                + "\",\"nombreTitular\":\"" + titular + "\"}";
    }

    private static String pagoValido() {
        return pagoJson("200.00", "PENDIENTE", "TARJETA", "2", "Ana Perez", "123", "ana@corpo.com");
    }

    private static MockHttpServletRequestBuilder registrar(String json) {
        return post("/pagos").contentType(APPLICATION_JSON).content(json);
    }

    @Test
    @DisplayName("UT-API-18 [CON-1] Listar pagos sin autenticacion responde 401 o 403")
    void listarPagosRequiereAutenticacion() throws Exception {
        assertThat(ApiTestSupport.status(mvc, get("/pagos"))).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-19 [CON-5] El listado de pagos no expone el CVV")
    void listadoPagosSinCvv() throws Exception {
        when(pagoService.obtenerTodosLosPagos()).thenReturn(List.of(PagoResponseDTO.builder()
                .id(1).emailUsuario("ana@corpo.com").monto(new BigDecimal("200.00")).metodo("TARJETA")
                .estado("PENDIENTE").numeroTarjeta("4111111111111111").cvv("123").nombreTitular("Ana").build()));

        String body = ApiTestSupport.respuesta(mvc, get("/pagos")).getContentAsString();

        assertThat(body).doesNotContain("\"cvv\"");
    }

    @Test
    @DisplayName("UT-API-20 [CON-1] Consultar un pago por id sin autenticacion responde 401 o 403")
    void consultarPagoRequiereAutenticacion() throws Exception {
        assertThat(ApiTestSupport.status(mvc, get("/pagos/1"))).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-21 [CON-1] Registrar un pago sin autenticacion responde 401 o 403")
    void registrarPagoRequiereAutenticacion() throws Exception {
        assertThat(ApiTestSupport.status(mvc, registrar(pagoValido()))).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-22 [CON-1] Modificar un pago sin autenticacion responde 401 o 403")
    void modificarPagoRequiereAutenticacion() throws Exception {
        int status = ApiTestSupport.status(mvc, put("/pagos/1").contentType(APPLICATION_JSON).content(pagoValido()));

        assertThat(status).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-23 [CON-1] Eliminar un pago sin autenticacion responde 401 o 403")
    void eliminarPagoRequiereAutenticacion() throws Exception {
        assertThat(ApiTestSupport.status(mvc, delete("/pagos/1"))).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-24 [INT-1] Pago con monto 0 responde 400")
    void pagoMontoCero() throws Exception {
        String json = pagoJson("0", "PENDIENTE", "TARJETA", "2", "Ana Perez", "123", "ana@corpo.com");

        assertThat(ApiTestSupport.status(mvc, registrar(json))).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-25 [INT-1] Pago sin productos responde 400")
    void pagoSinProductos() throws Exception {
        String json = pagoValido().replaceAll("\\[\\{\"productoId\":1,\"cantidad\":2}]", "[]");

        assertThat(ApiTestSupport.status(mvc, registrar(json))).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-26 [INT-1] Pago con CVV no numerico responde 400")
    void pagoCvvNoNumerico() throws Exception {
        String json = pagoJson("200.00", "PENDIENTE", "TARJETA", "2", "Ana Perez", "12a", "ana@corpo.com");

        assertThat(ApiTestSupport.status(mvc, registrar(json))).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-27 [INT-1] Pago con estado fuera del dominio (HACKEADO) responde 400")
    void pagoEstadoFueraDeDominio() throws Exception {
        String json = pagoJson("200.00", "HACKEADO", "TARJETA", "2", "Ana Perez", "123", "ana@corpo.com");

        assertThat(ApiTestSupport.status(mvc, registrar(json))).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-28 [INT-4] Un cliente no puede registrar un pago ya COMPLETADO")
    void clienteNoFijaEstadoCompletado() throws Exception {
        String json = pagoJson("200.00", "COMPLETADO", "TARJETA", "2", "Ana Perez", "123", "ana@corpo.com");

        assertThat(ApiTestSupport.status(mvc, registrar(json))).isIn(400, 403);
    }

    @Test
    @DisplayName("UT-API-29 [INT-1] Pago con cantidad negativa de producto responde 400")
    void pagoCantidadNegativa() throws Exception {
        String json = pagoJson("200.00", "PENDIENTE", "TARJETA", "-3", "Ana Perez", "123", "ana@corpo.com");

        assertThat(ApiTestSupport.status(mvc, registrar(json))).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-30 [INT-1] Pago con metodo no admitido (BITCOIN) responde 400")
    void pagoMetodoNoAdmitido() throws Exception {
        String json = pagoJson("200.00", "PENDIENTE", "BITCOIN", "2", "Ana Perez", "123", "ana@corpo.com");

        assertThat(ApiTestSupport.status(mvc, registrar(json))).isEqualTo(400);
    }

    @Test
    @DisplayName("UT-API-31 [INT-1] Un titular con tilde y enie (Jose Pena con acentos) es aceptado")
    void titularConTildeAceptado() throws Exception {
        String json = pagoJson("200.00", "PENDIENTE", "TARJETA", "2", "José Peña", "123", "ana@corpo.com");

        assertThat(ApiTestSupport.status(mvc, registrar(json)))
                .as("La validacion no debe rechazar nombres venezolanos validos").isEqualTo(201);
    }

    @Test
    @DisplayName("UT-API-32 [ACC-2] No se puede registrar un pago a nombre de otro usuario sin sesion")
    void pagoANombreDeOtroUsuario() throws Exception {
        String json = pagoJson("200.00", "PENDIENTE", "TARJETA", "2", "Ana Perez", "123", "victima@corpo.com");

        assertThat(ApiTestSupport.status(mvc, registrar(json))).isIn(401, 403);
    }

    @Test
    @DisplayName("UT-API-33 [Control] Un pago valido llega al servicio y responde 201")
    void pagoValidoSeAcepta() throws Exception {
        assertThat(ApiTestSupport.status(mvc, registrar(pagoValido()))).isEqualTo(201);
    }
}
