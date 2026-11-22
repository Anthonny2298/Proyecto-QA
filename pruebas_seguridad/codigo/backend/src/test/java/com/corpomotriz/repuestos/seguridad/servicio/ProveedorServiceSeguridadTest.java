package com.corpomotriz.repuestos.seguridad.servicio;

import com.corpomotriz.repuestos.dto.crear.ProveedorCrearDTO;
import com.corpomotriz.repuestos.model.Producto;
import com.corpomotriz.repuestos.model.Proveedor;
import com.corpomotriz.repuestos.repository.ProductoRepository;
import com.corpomotriz.repuestos.repository.ProveedorRepository;
import com.corpomotriz.repuestos.service.ProveedorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProveedorService. HU Anadir Proveedores (ERS 1.1.1, RN-111-3: no se permiten duplicados),
 * Consultar, Modificar y Eliminar Proveedores (Brief v1.2).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProveedorService - seguridad")
class ProveedorServiceSeguridadTest {

    @Mock
    private ProveedorRepository proveedorRepository;
    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private ProveedorService proveedorService;

    private Proveedor existente;

    @BeforeEach
    void preparar() {
        existente = Proveedor.builder().id(1L).nombre("Bosch Venezuela").email("ventas@bosch.com")
                .telefono("+58 212 5550000").build();
        when(proveedorRepository.findAll()).thenReturn(List.of(existente));
        when(proveedorRepository.save(any(Proveedor.class))).thenAnswer(inv -> {
            Proveedor p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(2L);
            }
            return p;
        });
    }

    private static ProveedorCrearDTO dto(String nombre, String email) {
        return ProveedorCrearDTO.builder().nombre(nombre).telefono("+58 212 5551111").email(email)
                .localizacion("Caracas").especializacion("Frenos").plazoEntrega("7 dias").build();
    }

    @Test
    @DisplayName("UT-SRV-32 [INT-1] Un proveedor con el mismo nombre se rechaza (RN-111-3)")
    void proveedorDuplicadoPorNombre() {
        Throwable error = catchThrowable(() -> proveedorService.createProveedor(dto("Bosch Venezuela", "otro@bosch.com")));

        assertThat(error).isNotNull();
        verify(proveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-SRV-33 [INT-1] Un proveedor con el mismo correo se rechaza (RN-111-3)")
    void proveedorDuplicadoPorCorreo() {
        Throwable error = catchThrowable(() -> proveedorService.createProveedor(dto("Bosch Caracas", "ventas@bosch.com")));

        assertThat(error).isNotNull();
        verify(proveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-SRV-34 [INT-5] Eliminar un proveedor inexistente se rechaza y no borra nada")
    void eliminarInexistente() {
        when(proveedorRepository.existsById(77)).thenReturn(false);

        Throwable error = catchThrowable(() -> proveedorService.deleteProveedor(77));

        assertThat(error).isNotNull();
        verify(proveedorRepository, never()).deleteById(anyInt());
    }

    @Test
    @DisplayName("UT-SRV-35 [CON-6] Listar productos de un proveedor no falla si un producto no tiene proveedor")
    void productoSinProveedorNoProvocaNpe() {
        when(productoRepository.findByProveedorId(1)).thenReturn(List.of(
                Producto.builder().id(9L).nombre("Correa").precio(new BigDecimal("15.00")).cantidad(4).build()));

        assertThatCode(() -> proveedorService.obtenerProductosDTOPorProveedor(1)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UT-SRV-36 [INT-5] Modificar un proveedor inexistente se rechaza y no guarda")
    void modificarInexistente() {
        when(proveedorRepository.findById(77)).thenReturn(Optional.empty());

        Throwable error = catchThrowable(() -> proveedorService.updateProveedor(77, dto("X", "x@x.com")));

        assertThat(error).isNotNull();
        verify(proveedorRepository, never()).save(any());
    }
}
