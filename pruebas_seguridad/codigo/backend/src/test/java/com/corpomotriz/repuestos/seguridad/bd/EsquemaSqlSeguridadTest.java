package com.corpomotriz.repuestos.seguridad.bd;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Script SQL del esquema repuestos_db (aportado por Devony; src/test/resources/db/repuestos_db.sql)
 * ejecutado sobre MySQL 8 real. Cada prueba recrea el esquema para ser independiente e idempotente.
 * Se omite automaticamente si no hay Docker disponible.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("BD desde el script SQL (MySQL 8 en contenedor) - seguridad")
class EsquemaSqlSeguridadTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("repuestos_db").withUsername("root").withPassword("test");

    private Connection conexion;

    @BeforeEach
    void recrearEsquema() throws Exception {
        String servidor = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/?useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection c = DriverManager.getConnection(servidor, "root", "test");
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS repuestos_db");
            for (String sentencia : sentenciasDelScript()) {
                st.execute(sentencia);
            }
        }
        conexion = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", "test");
    }

    @AfterEach
    void cerrarConexion() throws SQLException {
        if (conexion != null) {
            conexion.close();
        }
    }

    /** Lee el script tal como se entrego, quita los comentarios -- y lo separa por punto y coma. */
    private static List<String> sentenciasDelScript() throws IOException {
        try (InputStream in = EsquemaSqlSeguridadTest.class.getResourceAsStream("/db/repuestos_db.sql")) {
            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String sinComentarios = Arrays.stream(script.split("\\R"))
                    .map(linea -> linea.contains("--") ? linea.substring(0, linea.indexOf("--")) : linea)
                    .collect(Collectors.joining("\n"));
            return Arrays.stream(sinComentarios.split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    private void ejecutar(String sql) throws SQLException {
        try (Statement st = conexion.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private Throwable intentar(String sql) {
        return catchThrowable(() -> ejecutar(sql));
    }

    private int columna(String tabla, String columna, String dato) throws SQLException {
        String sql = "SELECT " + dato + " FROM information_schema.columns WHERE table_schema = 'repuestos_db' "
                + "AND table_name = '" + tabla + "' AND column_name = '" + columna + "'";
        try (Statement st = conexion.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private boolean existeColumna(String tabla, String columna) throws SQLException {
        return columna(tabla, columna, "COUNT(*)") > 0;
    }

    private void datosBase() throws SQLException {
        ejecutar("INSERT INTO usuario (id, nombre, email, contrasena, rol) VALUES (1, 'Ana', 'ana@corpo.com', 'x', 'CLIENTE')");
        ejecutar("INSERT INTO proveedor (id, nombre) VALUES (1, 'Bosch')");
    }

    @Test
    @DisplayName("UT-SQL-01 [ACC-1] El script rechaza dos usuarios con el mismo correo")
    void correoUnico() throws SQLException {
        datosBase();

        Throwable error = intentar("INSERT INTO usuario (nombre, email, contrasena, rol) VALUES ('Ana 2', 'ana@corpo.com', 'y', 'CLIENTE')");

        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("UT-SQL-02 [INT-4] El script rechaza un rol fuera de CLIENTE/ADMINISTRADOR")
    void rolFueraDeDominio() {
        Throwable error = intentar("INSERT INTO usuario (nombre, email, contrasena, rol) VALUES ('Eva', 'eva@corpo.com', 'x', 'SUPERADMIN')");

        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("UT-SQL-03 [INT-1] El script rechaza un estado de pago fuera del dominio")
    void estadoFueraDeDominio() throws SQLException {
        datosBase();

        Throwable error = intentar("INSERT INTO pago (usuario_id, fecha_pago, monto, metodo, estado) "
                + "VALUES (1, NOW(), 200.00, 'TARJETA', 'HACKEADO')");

        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("UT-SQL-04 [INT-1] El script rechaza un producto con precio negativo")
    void precioNegativo() throws SQLException {
        datosBase();

        Throwable error = intentar("INSERT INTO producto (nombre, precio, cantidad, proveedor_id, usuario_creador_id) "
                + "VALUES ('Filtro', -5.00, 3, 1, 1)");

        assertThat(error).as("Falta CHECK (precio > 0)").isNotNull();
    }

    @Test
    @DisplayName("UT-SQL-05 [INT-1] El script rechaza un producto con cantidad negativa")
    void cantidadNegativa() throws SQLException {
        datosBase();

        Throwable error = intentar("INSERT INTO producto (nombre, precio, cantidad, proveedor_id, usuario_creador_id) "
                + "VALUES ('Filtro', 25.00, -1, 1, 1)");

        assertThat(error).as("Falta CHECK (cantidad >= 0)").isNotNull();
    }

    @Test
    @DisplayName("UT-SQL-06 [CON-5] El script no crea una columna para el CVV")
    void sinColumnaCvv() throws SQLException {
        assertThat(existeColumna("pago", "cvv_tarjeta")).isFalse();
    }

    @Test
    @DisplayName("UT-SQL-07 [CON-5] La columna numero_tarjeta solo admite los ultimos 4 digitos")
    void numeroTarjetaSoloUltimosCuatro() throws SQLException {
        assertThat(columna("pago", "numero_tarjeta", "CHARACTER_MAXIMUM_LENGTH")).isLessThanOrEqualTo(4);
    }

    @Test
    @DisplayName("UT-SQL-08 [CON-4] La columna contrasena admite la longitud de un hash bcrypt (60)")
    void contrasenaAdmiteHash() throws SQLException {
        assertThat(columna("usuario", "contrasena", "CHARACTER_MAXIMUM_LENGTH")).isGreaterThanOrEqualTo(60);
    }

    @Test
    @DisplayName("UT-SQL-09 [INT-5] El script rechaza un producto sin proveedor")
    void productoSinProveedor() throws SQLException {
        datosBase();

        Throwable error = intentar("INSERT INTO producto (nombre, precio, cantidad, proveedor_id, usuario_creador_id) "
                + "VALUES ('Filtro', 25.00, 3, NULL, 1)");

        assertThat(error).as("proveedor_id admite NULL en el script").isNotNull();
    }

    @Test
    @DisplayName("UT-SQL-10 [NOR-3] El script impide borrar un usuario que tiene pagos")
    void usuarioConPagosNoSeBorra() throws SQLException {
        datosBase();
        ejecutar("INSERT INTO pago (usuario_id, fecha_pago, monto, metodo, estado) VALUES (1, NOW(), 200.00, 'TARJETA', 'PENDIENTE')");

        Throwable error = intentar("DELETE FROM usuario WHERE id = 1");

        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("UT-SQL-11 [INT-3] pago_producto guarda el precio unitario para poder verificar el monto")
    void lineaGuardaPrecioUnitario() throws SQLException {
        assertThat(existeColumna("pago_producto", "precio_unitario")).isTrue();
    }

    @Test
    @DisplayName("UT-SQL-12 [ACC-2] La columna creado_por no tiene un valor por defecto ficticio")
    void creadoPorSinValorFicticio() throws SQLException {
        String sql = "SELECT COLUMN_DEFAULT FROM information_schema.columns WHERE table_schema = 'repuestos_db' "
                + "AND table_name = 'producto' AND column_name = 'creado_por'";
        String porDefecto;
        try (Statement st = conexion.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            porDefecto = rs.next() ? rs.getString(1) : null;
        }
        assertThat(porDefecto).as("El autor no puede ser 'valor_por_defecto'").isNotEqualTo("valor_por_defecto");
    }
}
