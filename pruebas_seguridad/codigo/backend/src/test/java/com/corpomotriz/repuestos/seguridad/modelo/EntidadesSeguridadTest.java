package com.corpomotriz.repuestos.seguridad.modelo;

import com.corpomotriz.repuestos.model.Pago;
import com.corpomotriz.repuestos.model.Producto;
import com.corpomotriz.repuestos.model.Usuario;
import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entidades JPA sin base de datos: que expongan o no datos sensibles y que declaren
 * los metadatos de unicidad y auditoria. Subcaracteristicas: confidencialidad, responsabilidad, no repudio.
 */
@DisplayName("Entidades JPA - seguridad")
class EntidadesSeguridadTest {

    private static final String TARJETA = "4111111111111111";

    private static boolean tieneAlgunCampo(Class<?> clase, Set<String> nombres) {
        return Arrays.stream(clase.getDeclaredFields()).anyMatch(f -> nombres.contains(f.getName()));
    }

    private static Pago pagoConTarjeta() {
        return Pago.builder().id(1).monto(new BigDecimal("200.00")).metodo("TARJETA").estado("PENDIENTE")
                .numeroTarjeta(TARJETA).cvvTarjeta("987").nombreTitularTarjeta("Ana").build();
    }

    @Test
    @DisplayName("UT-ENT-01 [CON-7] Usuario.toString no incluye la contrasena")
    void usuarioToStringSinContrasena() {
        Usuario u = Usuario.builder().id(1L).nombre("Ana").email("ana@corpo.com").contrasena("Secreta1").rol("CLIENTE").build();

        assertThat(u.toString()).doesNotContain("Secreta1");
    }

    @Test
    @DisplayName("UT-ENT-02 [CON-7] Pago.toString no incluye el CVV")
    void pagoToStringSinCvv() {
        assertThat(pagoConTarjeta().toString()).doesNotContain("987");
    }

    @Test
    @DisplayName("UT-ENT-03 [CON-7] Pago.toString no incluye el numero de tarjeta")
    void pagoToStringSinTarjeta() {
        assertThat(pagoConTarjeta().toString()).doesNotContain(TARJETA);
    }

    @Test
    @DisplayName("UT-ENT-04 [CON-5] La entidad Pago no tiene campo para guardar el CVV (PCI DSS)")
    void pagoSinCampoCvv() {
        assertThat(tieneAlgunCampo(Pago.class, Set.of("cvvTarjeta", "cvv"))).isFalse();
    }

    @Test
    @DisplayName("UT-ENT-05 [ACC-1] Usuario.email esta declarado unico")
    void usuarioEmailUnico() throws NoSuchFieldException {
        Field email = Usuario.class.getDeclaredField("email");
        Column columna = email.getAnnotation(Column.class);

        assertThat(columna != null && columna.unique()).as("@Column(unique = true) en Usuario.email").isTrue();
    }

    @Test
    @DisplayName("UT-ENT-06 [NOR-2] Producto registra quien lo modifico por ultima vez")
    void productoConAuditoria() {
        assertThat(tieneAlgunCampo(Producto.class, Set.of("modificadoPor", "actualizadoPor", "lastModifiedBy"))).isTrue();
    }

    @Test
    @DisplayName("UT-ENT-07 [NOR-2] Pago registra quien y cuando lo modifico")
    void pagoConAuditoria() {
        assertThat(tieneAlgunCampo(Pago.class, Set.of("modificadoPor", "fechaModificacion", "lastModifiedBy",
                "lastModifiedDate"))).isTrue();
    }

    @Test
    @DisplayName("UT-ENT-08 [CON-4] La columna contrasena admite la longitud de un hash bcrypt (60)")
    void columnaContrasenaAdmiteHash() throws NoSuchFieldException {
        Column columna = Usuario.class.getDeclaredField("contrasena").getAnnotation(Column.class);
        int longitud = columna == null ? 255 : columna.length();

        assertThat(longitud).isGreaterThanOrEqualTo(60);
    }

    @Test
    @DisplayName("UT-ENT-09 [INT-5] Un Pago construido con builder inicializa su lista de productos")
    void pagoBuilderInicializaLista() {
        assertThat(Pago.builder().build().getPagoProductos())
                .as("@Builder ignora el inicializador sin @Builder.Default").isNotNull();
    }
}
