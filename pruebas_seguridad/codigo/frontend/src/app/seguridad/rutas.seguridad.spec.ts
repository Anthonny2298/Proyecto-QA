// Definicion de rutas (app.routes.ts): cada pantalla restringida declara RoleGuard y los roles del ERS/Brief.
import { Route } from '@angular/router';
import { routes } from '../app.routes';
import { RoleGuard } from '../guard/role.guard';

describe('Rutas de la aplicacion - seguridad', () => {
  function ruta(path: string): Route {
    const encontrada = routes.find(r => r.path === path);
    if (!encontrada) {
      throw new Error(`La ruta ${path} no existe`);
    }
    return encontrada;
  }

  function roles(path: string): string[] {
    return (ruta(path).data?.['roles'] as string[]) ?? [];
  }

  function protegida(path: string): boolean {
    return (ruta(path).canActivate ?? []).includes(RoleGuard);
  }

  it('UT-FE-06 [CON-2] pagos/:id no abre el formulario de edicion a un CLIENTE (Modificar Pago es del Administrador)', () => {
    expect(roles('pagos/:id')).not.toContain('CLIENTE');
  });

  it('UT-FE-07 [CON-2] pagos/editar/:id es exclusiva del ADMINISTRADOR', () => {
    expect(protegida('pagos/editar/:id')).toBeTrue();
    expect(roles('pagos/editar/:id')).toEqual(['ADMINISTRADOR']);
  });

  it('UT-FE-08 [CON-2] usuarios/registrados es exclusiva del ADMINISTRADOR', () => {
    expect(protegida('usuarios/registrados')).toBeTrue();
    expect(roles('usuarios/registrados')).toEqual(['ADMINISTRADOR']);
  });

  it('UT-FE-09 [CON-2] todas las rutas de proveedores exigen ADMINISTRADOR (alcance HU 111)', () => {
    const deProveedores = routes.filter(r => (r.path ?? '').startsWith('proveedores'));

    expect(deProveedores.length).toBeGreaterThan(0);
    deProveedores.forEach(r => {
      expect((r.canActivate ?? []).includes(RoleGuard)).withContext(r.path ?? '').toBeTrue();
      expect(r.data?.['roles']).withContext(r.path ?? '').toEqual(['ADMINISTRADOR']);
    });
  });

  it('UT-FE-10 [AUT-1] la ruta perfil exige una sesion activa', () => {
    expect((ruta('perfil').canActivate ?? []).length).toBeGreaterThan(0);
  });

  it('UT-FE-11 [CON-2] productos/nuevos es exclusiva del ADMINISTRADOR (alcance HU 112)', () => {
    expect(protegida('productos/nuevos')).toBeTrue();
    expect(roles('productos/nuevos')).toEqual(['ADMINISTRADOR']);
  });

  it('UT-FE-12 [CON-2] pagos/crear es exclusiva del CLIENTE (alcance HU 114)', () => {
    expect(roles('pagos/crear')).toEqual(['CLIENTE']);
  });
});
