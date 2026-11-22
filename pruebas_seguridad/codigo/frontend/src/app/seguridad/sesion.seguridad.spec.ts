// Manejo de la sesion en el navegador: AuthService y UsuarioService (autenticidad, confidencialidad, responsabilidad).
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { UsuarioService } from '../services/usuario.service';

describe('Sesion del usuario - seguridad', () => {
  let router: jasmine.SpyObj<Router>;

  function configurar(): void {
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    // Promesa que no se resuelve: evita el window.location.reload() de logout dentro de Karma.
    router.navigate.and.returnValue(new Promise<boolean>(() => undefined));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: Router, useValue: router }]
    });
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('UT-FE-17 [AUT-2] una sesion guardada hace 48 horas ya no se considera autenticada', () => {
    const haceDosDias = new Date(Date.now() - 48 * 60 * 60 * 1000).toISOString();
    localStorage.setItem('usuario', JSON.stringify({ id: 2, email: 'ana@corpo.com', rol: 'CLIENTE', fechaLogin: haceDosDias }));
    configurar();

    expect(TestBed.inject(AuthService).isAuthenticated()).toBeFalse();
  });

  it('UT-FE-18 [AUT-2] cerrar sesion elimina los datos de sesion del navegador', () => {
    localStorage.setItem('usuario', JSON.stringify({ id: 2, email: 'ana@corpo.com', rol: 'CLIENTE' }));
    configurar();
    const auth = TestBed.inject(AuthService);

    auth.logout();

    expect(localStorage.getItem('usuario')).toBeNull();
    expect(auth.isAuthenticated()).toBeFalse();
  });

  it('UT-FE-19 [ACC-3] la sesion no se guarda en localStorage, accesible a cualquier script de la pagina', () => {
    configurar();

    TestBed.inject(UsuarioService).setUsuario({ id: 2, nombre: 'Ana', email: 'ana@corpo.com', rol: 'CLIENTE' });

    expect(localStorage.getItem('usuario')).toBeNull();
  });

  it('UT-FE-20 [CON-5] la contrasena nunca se guarda en el almacenamiento del navegador', () => {
    configurar();

    TestBed.inject(UsuarioService).setUsuario({ id: 2, nombre: 'Ana', email: 'ana@corpo.com', rol: 'CLIENTE', contrasena: 'Secreta1' });

    expect(localStorage.getItem('usuario') ?? '').not.toContain('Secreta1');
  });

  it('UT-FE-21 [CON-1] las credenciales de login viajan por HTTPS', () => {
    configurar();
    const http = TestBed.inject(HttpTestingController);

    TestBed.inject(UsuarioService).login('ana@corpo.com', 'Secreta1').subscribe();
    const req = http.expectOne(() => true);

    expect(req.request.url.startsWith('https://')).withContext(req.request.url).toBeTrue();
    req.flush({});
    http.verify();
  });

  it('UT-FE-22 [CON-1] la contrasena viaja en el cuerpo y no en la URL', () => {
    configurar();
    const http = TestBed.inject(HttpTestingController);

    TestBed.inject(UsuarioService).login('ana@corpo.com', 'Secreta1').subscribe();
    const req = http.expectOne(() => true);

    expect(req.request.urlWithParams).not.toContain('Secreta1');
    expect(req.request.method).toBe('POST');
    req.flush({});
    http.verify();
  });
});
