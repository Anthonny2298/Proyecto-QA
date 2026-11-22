package com.corpomotriz.repuestos.seguridad.dto;

import com.corpomotriz.repuestos.dto.PagoProductoDTO;
import com.corpomotriz.repuestos.dto.crear.ProductoCrearDTO;
import com.corpomotriz.repuestos.dto.crear.ProveedorCrearDTO;
import com.corpomotriz.repuestos.dto.crear.UsuarioCrearDTO;
import com.corpomotriz.repuestos.dto.request.LoginRequestDTO;
import com.corpomotriz.repuestos.dto.request.PagoRequestDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de validacion declaradas en los DTO de entrada (Bean Validation).
 * Subcaracteristica ISO/IEC 25010:2023: integridad (INT-1, validacion de entradas).
 */
@DisplayName("DTO de entrada - validacion de seguridad")
class ValidacionDtoSeguridadTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void crearValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrar() {
        factory.close();
    }

    private static <T> Set<ConstraintViolation<T>> validar(T objeto) {
        return validator.validate(objeto);
    }

    private static ProductoCrearDTO producto(String precio, int cantidad) {
        return ProductoCrearDTO.builder().nombre("Filtro").descripcion("Filtro de aceite").precio(new BigDecimal(precio))
                .cantidad(cantidad).proveedorId(1).creadoPorId(1).marca("Bosch").build();
    }

    private static PagoRequestDTO.PagoRequestDTOBuilder pago() {
        return PagoRequestDTO.builder().emailUsuario("ana@corpo.com").monto(new BigDecimal("200.00"))
                .metodo("TARJETA").estado("PENDIENTE")
                .productos(List.of(PagoProductoDTO.builder().productoId(1).cantidad(2).build()))
                .numeroTarjeta("4111111111111111").fechaExpiracion("12/30").cvv("123").nombreTitular("Ana Perez");
    }

    @Test
    @DisplayName("UT-DTO-01 [INT-1] ProductoCrearDTO con precio 0 es invalido")
    void productoPrecioCero() {
        assertThat(validar(producto("0", 5))).isNotEmpty();
    }

    @Test
    @DisplayName("UT-DTO-02 [INT-1] ProductoCrearDTO con cantidad 0 es invalido (ERS: mayor a cero)")
    void productoCantidadCero() {
        assertThat(validar(producto("25.50", 0))).isNotEmpty();
    }

    @Test
    @DisplayName("UT-DTO-03 [INT-1] ProductoCrearDTO con cantidad negativa es invalido")
    void productoCantidadNegativa() {
        assertThat(validar(producto("25.50", -1))).isNotEmpty();
    }

    @Test
    @DisplayName("UT-DTO-04 [INT-4] UsuarioCrearDTO no admite el rol ADMINISTRADOR en el registro publico")
    void usuarioRolAdministrador() {
        UsuarioCrearDTO dto = UsuarioCrearDTO.builder().nombre("Ana").email("ana@corpo.com")
                .contrasena("Secreta1").rol("ADMINISTRADOR").build();

        assertThat(validar(dto)).isNotEmpty();
    }

    @Test
    @DisplayName("UT-DTO-05 [CON-4] UsuarioCrearDTO exige al menos 8 caracteres de contrasena (OWASP ASVS V2.1.1)")
    void usuarioContrasenaDebil() {
        UsuarioCrearDTO dto = UsuarioCrearDTO.builder().nombre("Ana").email("ana@corpo.com")
                .contrasena("aaaaaa").rol("CLIENTE").build();

        assertThat(validar(dto)).isNotEmpty();
    }

    @Test
    @DisplayName("UT-DTO-06 [INT-1] PagoRequestDTO con estado fuera de PENDIENTE/COMPLETADO/RECHAZADO es invalido")
    void pagoEstadoFueraDeDominio() {
        assertThat(validar(pago().estado("HACKEADO").build())).isNotEmpty();
    }

    @Test
    @DisplayName("UT-DTO-07 [INT-1] PagoRequestDTO con metodo distinto de TARJETA es invalido")
    void pagoMetodoNoAdmitido() {
        assertThat(validar(pago().metodo("BITCOIN").build())).isNotEmpty();
    }

    @Test
    @DisplayName("UT-DTO-08 [INT-1] PagoProductoDTO con cantidad negativa es invalido")
    void lineaCantidadNegativa() {
        assertThat(validar(PagoProductoDTO.builder().productoId(1).cantidad(-3).build())).isNotEmpty();
    }

    @Test
    @DisplayName("UT-DTO-09 [INT-1] PagoProductoDTO sin productoId es invalido")
    void lineaSinProducto() {
        assertThat(validar(PagoProductoDTO.builder().productoId(null).cantidad(1).build())).isNotEmpty();
    }

    @Test
    @DisplayName("UT-DTO-10 [INT-1] PagoRequestDTO acepta un titular con tilde y enie")
    void pagoTitularConTilde() {
        assertThat(validar(pago().nombreTitular("José Peña").build())).isEmpty();
    }

    @Test
    @DisplayName("UT-DTO-11 [INT-1] PagoRequestDTO con CVV de 5 digitos es invalido")
    void pagoCvvLargo() {
        assertThat(validar(pago().cvv("12345").build())).isNotEmpty();
    }

    @Test
    @DisplayName("UT-DTO-12 [INT-1] PagoRequestDTO con letras en el numero de tarjeta es invalido")
    void pagoTarjetaConLetras() {
        assertThat(validar(pago().numeroTarjeta("4111abcd11111111").build())).isNotEmpty();
    }

    @Test
    @DisplayName("UT-DTO-13 [INT-1] LoginRequestDTO sin correo es invalido")
    void loginSinCorreo() {
        LoginRequestDTO dto = new LoginRequestDTO();
        dto.setEmail("");
        dto.setContrasena("Secreta1");

        assertThat(validar(dto)).isNotEmpty();
    }

    @Test
    @DisplayName("UT-DTO-14 [INT-1] ProveedorCrearDTO con letras en el telefono es invalido")
    void proveedorTelefonoConLetras() {
        ProveedorCrearDTO dto = ProveedorCrearDTO.builder().nombre("Bosch").telefono("telefono-falso")
                .email("ventas@bosch.com").localizacion("Caracas").especializacion("Frenos").plazoEntrega("7 dias").build();

        assertThat(validar(dto)).isNotEmpty();
    }

    @Test
    @DisplayName("UT-DTO-15 [INT-1] PagoRequestDTO con monto negativo es invalido")
    void pagoMontoNegativo() {
        assertThat(validar(pago().monto(new BigDecimal("-50.00")).build())).isNotEmpty();
    }
}
