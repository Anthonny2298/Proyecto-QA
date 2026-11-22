package com.corpomotriz.repuestos.seguridad.bd;

import com.corpomotriz.repuestos.model.Pago;
import com.corpomotriz.repuestos.model.PagoProducto;
import com.corpomotriz.repuestos.model.Producto;
import com.corpomotriz.repuestos.model.Proveedor;
import com.corpomotriz.repuestos.model.Usuario;
import com.corpomotriz.repuestos.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Esquema generado por Hibernate desde las entidades del codigo (commit 51aae5a) sobre MySQL 8 real.
 * Se omite automaticamente si no hay Docker disponible.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("BD desde entidades JPA (MySQL 8 en contenedor) - seguridad")
class EntidadesMysqlSeguridadTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("repuestos_db");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private static Usuario usuario(String email, String rol) {
        return Usuario.builder().nombre("Ana").email(email).contrasena("x").rol(rol)
                .fechaCreacion(LocalDateTime.now()).build();
    }

    private Producto productoValido(Usuario creador, Proveedor proveedor, String precio, int cantidad) {
        return Producto.builder().nombre("Filtro").descripcion("Filtro").precio(new BigDecimal(precio)).cantidad(cantidad)
                .marca("Bosch").proveedor(proveedor).creadoPor(creador).fechaCreacion(LocalDateTime.now()).build();
    }

    private Proveedor proveedor() {
        return em.persistFlushFind(Proveedor.builder().nombre("Bosch").email("ventas@bosch.com").build());
    }

    private Pago pago(Usuario u, String estado) {
        return Pago.builder().usuario(u).monto(new BigDecimal("200.00")).metodo("TARJETA").estado(estado)
                .fechaPago(LocalDateTime.now()).build();
    }

    private Number escalar(String sql) {
        return (Number) em.getEntityManager().createNativeQuery(sql).getSingleResult();
    }

    @Test
    @DisplayName("UT-BD-01 [ACC-1] La BD rechaza dos usuarios con el mismo correo")
    void correoUnicoEnBd() {
        em.persistAndFlush(usuario("ana@corpo.com", "CLIENTE"));

        Throwable error = catchThrowable(() -> em.persistAndFlush(usuario("ana@corpo.com", "CLIENTE")));

        assertThat(error).as("Las entidades no declaran UNIQUE en usuario.email").isNotNull();
    }

    @Test
    @DisplayName("UT-BD-02 [CON-5] La tabla pago no tiene columna para el CVV")
    void sinColumnaCvv() {
        Number columnas = escalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'repuestos_db' "
                + "AND table_name = 'pago' AND column_name = 'cvv_tarjeta'");

        assertThat(columnas.intValue()).isZero();
    }

    @Test
    @DisplayName("UT-BD-03 [INT-1] La BD rechaza un producto con precio negativo")
    void precioNegativoRechazado() {
        Usuario creador = em.persistFlushFind(usuario("admin@corpo.com", "ADMINISTRADOR"));

        Throwable error = catchThrowable(() -> em.persistAndFlush(productoValido(creador, proveedor(), "-5.00", 3)));

        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("UT-BD-04 [INT-1] La BD rechaza un producto con cantidad negativa")
    void cantidadNegativaRechazada() {
        Usuario creador = em.persistFlushFind(usuario("admin@corpo.com", "ADMINISTRADOR"));

        Throwable error = catchThrowable(() -> em.persistAndFlush(productoValido(creador, proveedor(), "25.00", -1)));

        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("UT-BD-05 [INT-5] La BD rechaza una linea de pago sin cantidad")
    void lineaSinCantidadRechazada() {
        Usuario u = em.persistFlushFind(usuario("ana@corpo.com", "CLIENTE"));
        Producto p = em.persistFlushFind(productoValido(u, proveedor(), "25.00", 3));
        Pago pg = em.persistFlushFind(pago(u, "PENDIENTE"));

        Throwable error = catchThrowable(() -> em.persistAndFlush(
                PagoProducto.builder().pago(pg).producto(p).cantidad(null).build()));

        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("UT-BD-06 [INT-5] La BD rechaza un producto sin proveedor")
    void productoSinProveedorRechazado() {
        Usuario creador = em.persistFlushFind(usuario("admin@corpo.com", "ADMINISTRADOR"));

        Throwable error = catchThrowable(() -> em.persistAndFlush(productoValido(creador, null, "25.00", 3)));

        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("UT-BD-07 [INT-2] findByEmail con una inyeccion SQL clasica no devuelve usuarios")
    void findByEmailResisteInyeccion() {
        em.persistAndFlush(usuario("ana@corpo.com", "CLIENTE"));

        assertThat(usuarioRepository.findByEmail("' OR '1'='1")).isEmpty();
    }

    @Test
    @DisplayName("UT-BD-08 [INT-4] La BD rechaza un rol fuera de CLIENTE/ADMINISTRADOR")
    void rolFueraDeDominio() {
        Throwable error = catchThrowable(() -> em.persistAndFlush(usuario("eva@corpo.com", "SUPERADMIN")));

        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("UT-BD-09 [INT-1] La BD rechaza un estado de pago fuera del dominio")
    void estadoFueraDeDominio() {
        Usuario u = em.persistFlushFind(usuario("ana@corpo.com", "CLIENTE"));

        Throwable error = catchThrowable(() -> em.persistAndFlush(pago(u, "HACKEADO")));

        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("UT-BD-10 [NOR-3] No se puede borrar un usuario que tiene pagos (se conserva la evidencia)")
    void usuarioConPagosNoSeBorra() {
        Usuario u = em.persistFlushFind(usuario("ana@corpo.com", "CLIENTE"));
        em.persistAndFlush(pago(u, "PENDIENTE"));

        Throwable error = catchThrowable(() -> {
            em.remove(u);
            em.flush();
        });

        assertThat(error).isNotNull();
    }
}
