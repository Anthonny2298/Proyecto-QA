// HU Examinar y Editar Perfil (Brief v1.2): formulario de edicion del perfil.
import { Router } from '@angular/router';
import { EMPTY } from 'rxjs';
import { PerfilComponent } from '../components/perfil/perfil.component';
import { UsuarioService } from '../services/usuario.service';
import { AuthService } from '../services/auth.service';

describe('Perfil - seguridad', () => {
  let usuarioService: jasmine.SpyObj<UsuarioService>;

  function perfil(): PerfilComponent {
    usuarioService = jasmine.createSpyObj<UsuarioService>('UsuarioService', ['modificarUsuario', 'setUsuario']);
    usuarioService.modificarUsuario.and.returnValue(EMPTY);
    const router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    const auth = { usuarioValue: null } as unknown as AuthService;
    const c = new PerfilComponent(usuarioService, router, auth);
    c.usuario = { id: 2, nombre: 'Ana', email: 'ana@corpo.com', rol: 'CLIENTE' };
    c.editedUsuario = { id: 2, nombre: 'Ana', email: 'ana@corpo.com', rol: 'CLIENTE' };
    return c;
  }

  beforeEach(() => spyOn(window, 'alert'));

  it('UT-FE-37 [INT-1] la validacion del correo acepta dominios institucionales (@ucab.edu.ve)', () => {
    const c = perfil();
    c.editedUsuario!.email = 'ana@ucab.edu.ve';

    c.onEmailChange();

    expect(c.emailInvalid).toBeFalse();
  });

  it('UT-FE-38 [INT-4] guardar el perfil no envia el rol guardado en el navegador', () => {
    const c = perfil();
    c.editedUsuario!.rol = 'ADMINISTRADOR';
    c.newPassword = 'Nueva123';
    c.confirmPassword = 'Nueva123';

    c.guardarCambios();

    expect(usuarioService.modificarUsuario).toHaveBeenCalled();
    const enviado = usuarioService.modificarUsuario.calls.mostRecent().args[1] as unknown as Record<string, unknown>;
    expect(Object.keys(enviado)).not.toContain('rol');
  });

  it('UT-FE-39 [Control] si las contrasenas no coinciden no se envia nada', () => {
    const c = perfil();
    c.newPassword = 'Nueva123';
    c.confirmPassword = 'Otra4567';

    c.guardarCambios();

    expect(usuarioService.modificarUsuario).not.toHaveBeenCalled();
  });
});
