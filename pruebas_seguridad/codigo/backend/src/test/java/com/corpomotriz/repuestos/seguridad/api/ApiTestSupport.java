package com.corpomotriz.repuestos.seguridad.api;

import jakarta.servlet.ServletException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Utilidades comunes de las pruebas de API.
 * Una excepcion no controlada en el controlador sale de MockMvc como ServletException;
 * aqui se traduce a HTTP 500, que es lo que recibiria el cliente real. Asi la prueba
 * falla por su asercion (defecto detectado) y no termina en error de ejecucion.
 */
final class ApiTestSupport {

    private ApiTestSupport() {
    }

    static int status(MockMvc mvc, RequestBuilder request) throws Exception {
        MockHttpServletResponse response = respuesta(mvc, request);
        return response == null ? 500 : response.getStatus();
    }

    static MockHttpServletResponse respuesta(MockMvc mvc, RequestBuilder request) throws Exception {
        try {
            return mvc.perform(request).andReturn().getResponse();
        } catch (ServletException e) {
            return null;
        }
    }
}
