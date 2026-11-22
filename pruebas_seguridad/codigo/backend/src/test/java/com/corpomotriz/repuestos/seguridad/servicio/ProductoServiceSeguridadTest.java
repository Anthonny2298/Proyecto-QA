package com.corpomotriz.repuestos.seguridad.servicio;

import com.corpomotriz.repuestos.dto.crear.ProductoCrearDTO;
import com.corpomotriz.repuestos.model.Producto;
import com.corpomotriz.repuestos.model.Proveedor;
import com.corpomotriz.repuestos.model.Usuario;
import com.corpomotriz.repuestos.repository.ProductoRepository;
import com.corpomotriz.repuestos.repository.ProveedorRepository;
import com.corpomotriz.repuestos.repository.UsuarioRepository;
import com.corpomotriz.repuestos.service.ProductoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductoService. HU Insertar Producto (ERS 1.1.2), Consultar, Modificar y Eliminar Producto (Brief v1.2).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductoService - seguridad")
class ProductoServiceSeguridadTest {

    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private ProveedorRepository proveedorRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ProductoService productoService;

    private Usuario admin;
    private Usuario otro;

    @BeforeEach
    void preparar() {
        admin = Usuario.builder().id(5L).nombre("Admin").email("admin@corpo.com").contrasena("x").rol("ADMINISTRADOR").build();
        otro = Usuario.builder().id(99L).nombre("Otro").email("otro@corpo.com").contrasena("x").rol("CLIENTE").build();
        Proveedor bosch = Proveedor.builder().id(1L).nombre("Bosch").build();

        when(proveedorRepository.findById(1)).thenReturn(Optional.of(bosch));
        when(usuarioRepository.findById(5)).thenReturn(Optional.of(admin));
        when(usuarioRepository.findById(99)).thenReturn(Optional.of(otro));
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(otro));
        when(usuarioRepository.findByEmail("admin@corpo.com")).thenReturn(Optional.of(admin));
        when(productoRepository.findAll()).thenReturn(List.of());
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private static ProductoCrearDTO dto(String nombre, String precio, int cantidad, int creadoPorId) {
        return ProductoCrearDTO.builder().nombre(nombre).descripcion("Filtro de aceite").precio(new BigDecimal(precio))
                .cantidad(cantidad).proveedorId(1).creadoPorId(creadoPorId).marca("Bosch").build();
    }

    private Producto productoGuardado() {
        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("UT-SRV-25 [ACC-2] El creador del producto es el usuario autenticado, no el id del cuerpo")
    void creadorTomadoDeLaSesion() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@corpo.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))));

        productoService.createProducto(dto("Filtro", "25.50", 10, 99));

        assertThat(productoGuardado().getCreadoPor().getEmail()).isEqualTo("admin@corpo.com");
    }

    @Test
    @DisplayName("UT-SRV-26 [INT-1] El servicio rechaza precio 0 aunque el controlador no valide")
    void precioCeroRechazado() {
        Throwable error = catchThrowable(() -> productoService.createProducto(dto("Filtro", "0", 10, 5)));

        assertThat(error).isNotNull();
        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-SRV-27 [INT-1] El servicio rechaza una cantidad negativa")
    void cantidadNegativaRechazada() {
        Throwable error = catchThrowable(() -> productoService.createProducto(dto("Filtro", "25.50", -1, 5)));

        assertThat(error).isNotNull();
        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-SRV-28 [INT-1] Un producto con nombre ya existente se rechaza (RN-112-1)")
    void nombreDuplicadoRechazado() {
        when(productoRepository.findAll()).thenReturn(List.of(
                Producto.builder().id(7L).nombre("Filtro").precio(new BigDecimal("20.00")).cantidad(3).build()));

        Throwable error = catchThrowable(() -> productoService.createProducto(dto("Filtro", "25.50", 10, 5)));

        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("UT-SRV-29 [NOR-2] Modificar un producto conserva el creador original")
    void modificarConservaCreador() {
        Producto existente = Producto.builder().id(3L).nombre("Filtro").precio(new BigDecimal("20.00")).cantidad(3)
                .creadoPor(admin).build();
        when(productoRepository.findById(3)).thenReturn(Optional.of(existente));

        productoService.updateProducto(3, dto("Filtro", "25.50", 10, 1));

        assertThat(productoGuardado().getCreadoPor().getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("UT-SRV-30 [INT-5] Eliminar un producto inexistente devuelve false y no borra nada")
    void eliminarInexistente() {
        when(productoRepository.findById(42)).thenReturn(Optional.empty());

        assertThat(productoService.deleteProducto(42)).isFalse();
        verify(productoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("UT-SRV-31 [CON-6] Consultar un producto sin proveedor no lanza excepcion")
    void consultarProductoSinProveedor() {
        when(productoRepository.findByIdWithProveedor(8)).thenReturn(Optional.of(
                Producto.builder().id(8L).nombre("Correa").precio(new BigDecimal("15.00")).cantidad(4).build()));

        assertThatCode(() -> productoService.getProductoById(8)).doesNotThrowAnyException();
    }
}
