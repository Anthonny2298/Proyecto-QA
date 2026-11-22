-- Script del esquema repuestos_db aportado por Devony Ramirez (no esta en el repositorio).
-- Las sentencias SQL estan sin cambios; solo se quitaron tildes de los comentarios.
-- Lo ejecuta EsquemaSqlSeguridadTest.

-- Crear el schema
CREATE SCHEMA IF NOT EXISTS repuestos_db;
USE repuestos_db;

-- Tabla: proveedor
CREATE TABLE proveedor (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    telefono VARCHAR(20),
    email VARCHAR(255),
    localizacion VARCHAR(255),
    especializacion VARCHAR(255),
    plazo_entrega VARCHAR(255),
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabla: usuario
CREATE TABLE usuario (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    contrasena VARCHAR(255) NOT NULL,
    rol ENUM('CLIENTE', 'ADMINISTRADOR') NOT NULL,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabla: producto
CREATE TABLE producto (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    descripcion VARCHAR(500),
    precio DECIMAL(10, 2) NOT NULL,
    cantidad INT NOT NULL,
    proveedor_id INT,
    usuario_creador_id INT,
    marca VARCHAR(255),
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    creado_por VARCHAR(255) NOT NULL DEFAULT 'valor_por_defecto',
    FOREIGN KEY (proveedor_id) REFERENCES proveedor(id),
    FOREIGN KEY (usuario_creador_id) REFERENCES usuario(id)
);

-- Tabla: pago
CREATE TABLE pago (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL,
    fecha_pago DATETIME NOT NULL, -- Cambiado a DATETIME para consistencia con LocalDateTime en Java
    monto DECIMAL(10, 2) NOT NULL,
    metodo VARCHAR(100) NOT NULL, -- Aumentado a 100
    estado ENUM('PENDIENTE', 'COMPLETADO', 'RECHAZADO') NOT NULL, -- Estado cambiado a COMPLETADO para coincidir con frontend
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- --- Nuevas columnas para la tarjeta de credito ---
    numero_tarjeta VARCHAR(20),       -- Para almacenar el numero (o ultimos 4)
    fecha_expiracion_tarjeta VARCHAR(5), -- Para MM/AA
    cvv_tarjeta VARCHAR(4),           -- Para CVV
    nombre_titular_tarjeta VARCHAR(255),
    -- --- Fin Nuevas columnas ---

    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);

-- Tabla: pedido
CREATE TABLE pedido (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL,
    producto_id INT NOT NULL,
    cantidad INT NOT NULL,
    fecha_pedido TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id)
);

-- Tabla: pago_producto
CREATE TABLE pago_producto (
    id INT AUTO_INCREMENT PRIMARY KEY,
    pago_id INT NOT NULL,
    producto_id INT NOT NULL,
    cantidad INT NOT NULL,
    CONSTRAINT fk_pago FOREIGN KEY (pago_id) REFERENCES pago(id),
    CONSTRAINT fk_producto FOREIGN KEY (producto_id) REFERENCES producto(id)
);

-- Indices recomendados
CREATE INDEX idx_producto_nombre ON producto(nombre);
CREATE INDEX idx_proveedor_nombre ON proveedor(nombre);
CREATE INDEX idx_usuario_email ON usuario(email);
CREATE INDEX idx_pago_producto_pago_id ON pago_producto(pago_id);
CREATE INDEX idx_pago_producto_producto_id ON pago_producto(producto_id);
