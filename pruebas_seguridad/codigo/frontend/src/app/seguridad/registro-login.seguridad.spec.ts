// HU Crear Perfil (ERS 1.1.3) e inicio de sesion: formularios de registro y login.
import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RegistroComponent } from '../components/registro/registro.component';
import { LoginComponent } from '../components/login/login.component';
import { UsuarioService } from '../services/usuario.service';

describe('Registro e inicio de sesion - seguridad', () => {
  let usuarioService: jasmine.SpyObj<UsuarioService>;
  let fb: FormBuilder;

  beforeEach(() => {
    usuarioService = jasmine.createSpyObj<UsuarioService>('UsuarioService', ['crearUsuario', 'login', 'setUsuario']);
    usuarioService.crearUsuario.and.returnValue(of({}));
    TestBed.configureTestingModule({});
    fb = TestBed.inject(FormBuilder);
  });

  function registro(): RegistroComponent {
    const c = new RegistroComponent(fb, usuarioService);
    c.ngOnInit();
    return c;
  }

  function login(): LoginComponent {
    const router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    const c = new LoginComponent(fb, usuarioService, router);
    c.ngOnInit();
    return c;
  }

  it('UT-FE-23 [INT-4] el formulario de registro no envia el rol: lo asigna el servidor', () => {
    expect(registro().registroForm.contains('rol')).toBeFalse();
  });

  it('UT-FE-24 [INT-1] un registro con contrasena de 5 caracteres no se envia', () => {
    const c = registro();
    c.registroForm.patchValue({ nombre: 'Ana', email: 'ana@corpo.com', contrasena: '12345' });

    c.onSubmit();

    expect(usuarioService.crearUsuario).not.toHaveBeenCalled();
  });

  it('UT-FE-25 [INT-1] un registro con correo mal formado no se envia', () => {
    const c = registro();
    c.registroForm.patchValue({ nombre: 'Ana', email: 'no-es-correo', contrasena: 'Secreta1' });

    c.onSubmit();

    expect(usuarioService.crearUsuario).not.toHaveBeenCalled();
  });

  it('UT-FE-26 [AUT-3] el login muestra el mismo mensaje para correo inexistente y clave incorrecta', () => {
    const c = login();
    c.loginForm.setValue({ email: 'ana@corpo.com', contrasena: 'Secreta1' });

    usuarioService.login.and.returnValue(throwError(() => ({ error: { message: 'Usuario no encontrado' } })));
    c.onSubmit();
    const mensajeCorreo = c.errorMessage;

    usuarioService.login.and.returnValue(throwError(() => ({ error: { message: 'Contraseña incorrecta' } })));
    c.onSubmit();
    const mensajeClave = c.errorMessage;

    expect(mensajeCorreo).toEqual(mensajeClave);
  });

  it('UT-FE-27 [CON-7] un login exitoso no imprime los datos del usuario en la consola', () => {
    const c = login();
    c.loginForm.setValue({ email: 'ana@corpo.com', contrasena: 'Secreta1' });
    usuarioService.login.and.returnValue(of({ message: 'ok', usuario: { id: 2, nombre: 'Ana', email: 'ana@corpo.com', rol: 'CLIENTE' } }));
    const log = spyOn(console, 'log');

    c.onSubmit();

    expect(log).not.toHaveBeenCalled();
  });
});
