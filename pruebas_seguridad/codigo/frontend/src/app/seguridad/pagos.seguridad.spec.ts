// HU Registrar Pago (ERS 1.1.4), Modificar y Consultar Pago: formulario de pago y lista de pagos.
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { EMPTY, of } from 'rxjs';
import { PagosRegistrarComponent } from '../components/pagos-registrar/pagos-registrar.component';
import { PagosListaComponent } from '../components/pagos-lista/pagos-lista.component';
import { PagosService } from '../services/pagos.service';
import { ProductoService } from '../services/producto.service';
import { AuthService } from '../services/auth.service';
import { PagoResponse } from '../model/pagos';

describe('Pagos - seguridad', () => {
  const TARJETA = '4111111111111111';
  let pagosService: jasmine.SpyObj<PagosService>;
  let productoService: jasmine.SpyObj<ProductoService>;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    pagosService = jasmine.createSpyObj<PagosService>('PagosService',
      ['agregarPago', 'actualizarPago', 'obtenerPagoPorId', 'obtenerPagosLista', 'eliminarPago']);
    pagosService.agregarPago.and.returnValue(EMPTY);
    pagosService.obtenerPagosLista.and.returnValue(of([]));
    productoService = jasmine.createSpyObj<ProductoService>('ProductoService', ['obtenerProductosLista']);
    productoService.obtenerProductosLista.and.returnValue(of([]));
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['getRol', 'getEmailUsuario']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    spyOn(window, 'alert');
  });

  function formulario(): PagosRegistrarComponent {
    const route = { paramMap: of(convertToParamMap({})) } as unknown as ActivatedRoute;
    return new PagosRegistrarComponent(route, router, pagosService, productoService, auth);
  }

  function pagoGuardado(): PagoResponse {
    return {
      id: 5, emailUsuario: 'ana@corpo.com', monto: 200, metodo: 'TARJETA', estado: 'PENDIENTE', fechaPago: '2025-06-01',
      productos: [{ productoId: 1, nombreProducto: 'Filtro', cantidad: 2 }],
      numeroTarjeta: TARJETA, fechaExpiracion: '12/30', cvv: '123', nombreTitular: 'Ana Perez'
    };
  }

  it('UT-FE-28 [INT-1] la validacion del titular acepta nombres con tilde y enie', () => {
    expect(formulario().validarNombreTitular('José Peña')).toBeTrue();
  });

  it('UT-FE-29 [INT-1] la validacion del CVV rechaza letras', () => {
    expect(formulario().validarCvv('12a')).toBeFalse();
  });

  it('UT-FE-30 [INT-1] la validacion del numero de tarjeta rechaza letras', () => {
    expect(formulario().validarNumeroTarjeta('4111abcd11111111')).toBeFalse();
  });

  it('UT-FE-31 [INT-1] la validacion de la fecha rechaza una tarjeta vencida', () => {
    expect(formulario().validarFechaExpiracion('01/20')).toBeFalse();
  });

  it('UT-FE-32 [INT-1] un pago con cantidad negativa no se envia al servidor', () => {
    const c = formulario();
    c.pago.metodo = '';
    c.pago.monto = 0;
    c.pago.productos = [{ productoId: 1, cantidad: -3 }];

    c.guardarPago();

    expect(pagosService.agregarPago).not.toHaveBeenCalled();
  });

  it('UT-FE-33 [CON-5] al editar un pago el formulario no recibe el CVV', () => {
    pagosService.obtenerPagoPorId.and.returnValue(of(pagoGuardado()));
    const c = formulario();

    c.cargarPagoParaEdicion(5);

    expect(c.pago.cvv ?? '').toBe('');
  });

  it('UT-FE-34 [CON-5] al editar un pago el formulario no muestra el numero completo de la tarjeta', () => {
    pagosService.obtenerPagoPorId.and.returnValue(of(pagoGuardado()));
    const c = formulario();

    c.cargarPagoParaEdicion(5);

    expect(c.pago.numeroTarjeta).not.toBe(TARJETA);
  });

  function lista(rol: string | null, email: string | null): PagosListaComponent {
    auth.getRol.and.returnValue(rol);
    auth.getEmailUsuario.and.returnValue(email);
    const c = new PagosListaComponent(pagosService, router, auth);
    c.ngOnInit();
    c.pagos = [
      { ...pagoGuardado(), id: 1, emailUsuario: 'ana@corpo.com' },
      { ...pagoGuardado(), id: 2, emailUsuario: 'luis@corpo.com' }
    ];
    return c;
  }

  it('UT-FE-35 [Control] la lista de un CLIENTE solo muestra sus propios pagos', () => {
    const c = lista('CLIENTE', 'ana@corpo.com');

    expect(c.pagosFiltrados().map(p => p.id)).toEqual([1]);
  });

  it('UT-FE-36 [Control] sin rol la lista de pagos queda vacia', () => {
    const c = lista(null, null);

    expect(c.pagosFiltrados()).toEqual([]);
  });
});
