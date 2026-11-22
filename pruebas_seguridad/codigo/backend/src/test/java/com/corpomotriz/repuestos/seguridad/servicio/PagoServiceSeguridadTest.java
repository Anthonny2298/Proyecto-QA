package com.corpomotriz.repuestos.seguridad.servicio;

import com.corpomotriz.repuestos.dto.PagoProductoDTO;
import com.corpomotriz.repuestos.dto.request.PagoRequestDTO;
import com.corpomotriz.repuestos.dto.response.PagoResponseDTO;
import com.corpomotriz.repuestos.model.Pago;
import com.corpomotriz.repuestos.model.PagoProducto;
import com.corpomotriz.repuestos.model.Producto;
import com.corpomotriz.repuestos.model.Usuario;
import com.corpomotriz.repuestos.repository.PagoProductoRepository;
import com.corpomotriz.repuestos.repository.PagoRepository;
import com.corpomotriz.repuestos.repository.ProductoRepository;
import com.corpomotriz.repuestos.repository.UsuarioRepository;
import com.corpomotriz.repuestos.service.PagoService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PagoService. HU Registrar Pago (ERS 1.1.4), Modificar, Consultar y Eliminar Pago (Brief v1.2).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PagoService - seguridad")
class PagoServiceSeguridadTest {

    private static final String TARJETA = "4111111111111111";
    private static final String CVV = "987";

    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private PagoProductoRepository pagoProductoRepository;

    @InjectMocks
    private PagoService pagoService;

    private Usuario ana;
    private Producto filtro;

    @BeforeEach
    void preparar() {
        ana = Usuario.builder().id(1L).nombre("Ana").email("ana@corpo.com").contrasena("x").rol("CLIENTE").build();
        filtro = Producto.builder().id(1L).nombre("Filtro").precio(new BigDecimal("100.00")).cantidad(10).build();

        when(usuarioRepository.findByEmail("ana@corpo.com")).thenReturn(Optional.of(ana));
        when(productoRepository.findById(1)).thenReturn(Optional.of(filtro));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(1);
            }
            return p;
        });
        when(pagoProductoRepository.save(any(PagoProducto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static PagoRequestDTO pedido(String monto, String estado, String metodo, Integer cantidad, String email) {
        return PagoRequestDTO.builder().emailUsuario(email).monto(new BigDecimal(monto)).metodo(metodo).estado(estado)
                .productos(List.of(PagoProductoDTO.builder().productoId(1).cantidad(cantidad).build()))
                .numeroTarjeta(TARJETA).fechaExpiracion("12/30").cvv(CVV).nombreTitular("Ana Perez").build();
    }

    private static PagoRequestDTO pedidoValido(int cantidad) {
        return pedido("200.00", "PENDIENTE", "TARJETA", cantidad, "ana@corpo.com");
    }

    private Pago pagoGuardado() {
        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private Pago pagoExistente() {
        PagoProducto linea = PagoProducto.builder().id(1).producto(filtro).cantidad(2).build();
        List<PagoProducto> lineas = new ArrayList<>();
        lineas.add(linea);
        Pago pago = Pago.builder().id(5).usuario(ana).monto(new BigDecimal("200.00")).metodo("TARJETA")
                .estado("PENDIENTE").fechaPago(LocalDateTime.of(2025, 6, 1, 10, 0)).pagoProductos(lineas)
                .numeroTarjeta(TARJETA).cvvTarjeta(CVV).build();
        linea.setPago(pago);
        return pago;
    }

    @Test
    @DisplayName("UT-SRV-09 [INT-3] El monto se recalcula en el servidor (precio x cantidad)")
    void montoRecalculadoEnServidor() {
        pagoService.registrarPago(pedido("1.00", "PENDIENTE", "TARJETA", 2, "ana@corpo.com"));

        assertThat(pagoGuardado().getMonto()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("UT-SRV-10 [INT-3] Una cantidad negativa se rechaza y el stock no aumenta")
    void cantidadNegativaRechazada() {
        Throwable error = catchThrowable(() -> pagoService.registrarPago(pedidoValido(-3)));

        assertThat(error).as("Debe rechazarse la cantidad negativa").isNotNull();
        assertThat(filtro.getCantidad()).isEqualTo(10);
    }

    @Test
    @DisplayName("UT-SRV-11 [INT-3] Una cantidad cero se rechaza")
    void cantidadCeroRechazada() {
        Throwable error = catchThrowable(() -> pagoService.registrarPago(pedidoValido(0)));

        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("UT-SRV-12 [CON-6] Una cantidad nula se rechaza con un error controlado, no NullPointerException")
    void cantidadNulaErrorControlado() {
        Throwable error = catchThrowable(() -> pagoService.registrarPago(
                pedido("200.00", "PENDIENTE", "TARJETA", null, "ana@corpo.com")));

        assertThat(error).isNotNull().isNotInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("UT-SRV-13 [INT-5] Con stock insuficiente el pago falla y el stock no se modifica")
    void stockInsuficienteNoModificaInventario() {
        Throwable error = catchThrowable(() -> pagoService.registrarPago(pedidoValido(50)));

        assertThat(error).isNotNull();
        verify(productoRepository, never()).save(any());
        assertThat(filtro.getCantidad()).isEqualTo(10);
    }

    @Test
    @DisplayName("UT-SRV-14 [INT-4] Un pago nuevo queda PENDIENTE aunque el cliente envie COMPLETADO")
    void estadoInicialPendiente() {
        pagoService.registrarPago(pedido("200.00", "COMPLETADO", "TARJETA", 2, "ana@corpo.com"));

        assertThat(pagoGuardado().getEstado()).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("UT-SRV-15 [CON-5] El CVV no se almacena")
    void cvvNoSeAlmacena() {
        pagoService.registrarPago(pedidoValido(2));

        assertThat(pagoGuardado().getCvvTarjeta()).isNull();
    }

    @Test
    @DisplayName("UT-SRV-16 [CON-5] El numero completo de la tarjeta no se almacena")
    void numeroCompletoNoSeAlmacena() {
        pagoService.registrarPago(pedidoValido(2));

        assertThat(pagoGuardado().getNumeroTarjeta()).isNotEqualTo(TARJETA);
    }

    @Test
    @DisplayName("UT-SRV-17 [CON-5] La respuesta de registrar pago no incluye el CVV")
    void respuestaRegistroSinCvv() {
        PagoResponseDTO respuesta = pagoService.registrarPago(pedidoValido(2));

        assertThat(respuesta.getCvv()).isNull();
    }

    @Test
    @DisplayName("UT-SRV-18 [CON-5] La respuesta de registrar pago enmascara el numero de tarjeta")
    void respuestaRegistroTarjetaEnmascarada() {
        PagoResponseDTO respuesta = pagoService.registrarPago(pedidoValido(2));

        assertThat(respuesta.getNumeroTarjeta()).isNotEqualTo(TARJETA);
    }

    @Test
    @DisplayName("UT-SRV-19 [CON-5] Consultar todos los pagos no expone el CVV")
    void consultarPagosSinCvv() {
        when(pagoRepository.findAll()).thenReturn(List.of(pagoExistente()));

        List<PagoResponseDTO> pagos = pagoService.obtenerTodosLosPagos();

        assertThat(pagos).allSatisfy(p -> assertThat(p.getCvv()).isNull());
    }

    @Test
    @DisplayName("UT-SRV-20 [ACC-2] Modificar un pago no permite reasignarlo a otro usuario")
    void modificarNoReasignaUsuario() {
        Usuario luis = Usuario.builder().id(2L).nombre("Luis").email("luis@corpo.com").contrasena("x").rol("CLIENTE").build();
        when(usuarioRepository.findByEmail("luis@corpo.com")).thenReturn(Optional.of(luis));
        when(pagoRepository.findById(5)).thenReturn(Optional.of(pagoExistente()));

        pagoService.modificarPago(5, pedido("200.00", "PENDIENTE", "TARJETA", 2, "luis@corpo.com"));

        assertThat(pagoGuardado().getUsuario().getEmail()).isEqualTo("ana@corpo.com");
    }

    @Test
    @DisplayName("UT-SRV-21 [INT-5] Modificar los productos de un pago actualiza el inventario")
    void modificarActualizaInventario() {
        when(pagoRepository.findById(5)).thenReturn(Optional.of(pagoExistente()));

        pagoService.modificarPago(5, pedidoValido(4));

        verify(productoRepository, atLeastOnce()).save(any(Producto.class));
    }

    @Test
    @DisplayName("UT-SRV-22 [INT-5] Eliminar un pago repone el stock de sus productos")
    void eliminarReponeStock() {
        when(pagoRepository.findById(5)).thenReturn(Optional.of(pagoExistente()));

        pagoService.eliminarPago(5);

        assertThat(filtro.getCantidad()).isEqualTo(12);
    }

    @Test
    @DisplayName("UT-SRV-23 [INT-1] Un metodo de pago no admitido se rechaza")
    void metodoNoAdmitidoRechazado() {
        Throwable error = catchThrowable(() -> pagoService.registrarPago(
                pedido("200.00", "PENDIENTE", "BITCOIN", 2, "ana@corpo.com")));

        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("UT-SRV-24 [INT-5] Eliminar un pago inexistente devuelve false y no borra nada")
    void eliminarInexistente() {
        when(pagoRepository.findById(99)).thenReturn(Optional.empty());

        boolean eliminado = pagoService.eliminarPago(99);

        assertThat(eliminado).isFalse();
        verify(pagoRepository, never()).delete(any());
    }
}
