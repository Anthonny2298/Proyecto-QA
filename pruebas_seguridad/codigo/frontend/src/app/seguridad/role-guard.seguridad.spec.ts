// RoleGuard: control de acceso a pantallas por rol (ISO/IEC 25010:2023, confidencialidad y autenticidad).
// HU con alcance restringido: Anadir/Consultar Proveedores, Insertar Producto, Registrar/Modificar Pago.
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { RoleGuard } from '../guard/role.guard';

describe('RoleGuard - seguridad', () => {
  let router: jasmine.SpyObj<Router>;

  function ruta(roles: string[]): ActivatedRouteSnapshot {
    return { data: { roles } } as unknown as ActivatedRouteSnapshot;
  }

  function crearGuard(usuarioGuardado: unknown | null): RoleGuard {
    localStorage.clear();
    if (usuarioGuardado) {
      localStorage.setItem('usuario', JSON.stringify(usuarioGuardado));
    }
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.returnValue(Promise.resolve(true));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: Router, useValue: router }] });
    return TestBed.inject(RoleGuard);
  }

  beforeEach(() => spyOn(window, 'alert'));
  afterEach(() => localStorage.clear());

  it('UT-FE-01 [AUT-1] sin sesion redirige a /iniciar-sesion y niega el acceso', () => {
    const guard = crearGuard(null);

    expect(guard.canActivate(ruta(['ADMINISTRADOR']))).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/iniciar-sesion']);
  });

  it('UT-FE-02 [CON-2] un CLIENTE no entra a una ruta de ADMINISTRADOR', () => {
    const guard = crearGuard({ id: 2, nombre: 'Ana', email: 'ana@corpo.com', rol: 'CLIENTE' });

    expect(guard.canActivate(ruta(['ADMINISTRADOR']))).toBeFalse();
  });

  it('UT-FE-03 [CON-2] un rol ADMINISTRADOR escrito a mano en localStorage no concede acceso', () => {
    // Sesion fabricada en el navegador, sin credencial emitida por el servidor.
    const guard = crearGuard({ id: 99, nombre: 'Atacante', email: 'x@x.com', rol: 'ADMINISTRADOR' });

    expect(guard.canActivate(ruta(['ADMINISTRADOR']))).toBeFalse();
  });

  it('UT-FE-04 [CON-2] una sesion sin rol no entra a rutas protegidas', () => {
    const guard = crearGuard({ id: 2, nombre: 'Ana', email: 'ana@corpo.com' });

    expect(guard.canActivate(ruta(['CLIENTE', 'ADMINISTRADOR']))).toBeFalse();
  });

  it('UT-FE-05 [Control] un ADMINISTRADOR con sesion entra a una ruta de ADMINISTRADOR', () => {
    const guard = crearGuard({ id: 1, nombre: 'Admin', email: 'admin@corpo.com', rol: 'ADMINISTRADOR', token: 'jwt' });

    expect(guard.canActivate(ruta(['ADMINISTRADOR']))).toBeTrue();
  });
});
