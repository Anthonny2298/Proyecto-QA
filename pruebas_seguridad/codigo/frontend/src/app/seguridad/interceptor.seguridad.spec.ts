// AuthInterceptor y su registro en app.config.ts (autenticidad: cada peticion debe llevar una credencial).
import { HTTP_INTERCEPTORS, HttpHandler, HttpRequest, HttpEvent } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { appConfig } from '../app.config';
import { AuthInterceptor } from '../interceptors/auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('AuthInterceptor - seguridad', () => {
  function interceptar(usuario: unknown | null): HttpRequest<unknown> {
    const authStub = { usuarioValue: usuario } as unknown as AuthService;
    const interceptor = new AuthInterceptor(authStub);
    let enviada!: HttpRequest<unknown>;
    const siguiente: HttpHandler = {
      handle: (req: HttpRequest<unknown>): Observable<HttpEvent<unknown>> => {
        enviada = req;
        return of();
      }
    };
    interceptor.intercept(new HttpRequest('GET', 'http://localhost:8080/pagos'), siguiente).subscribe();
    return enviada;
  }

  // Recorre los proveedores de appConfig (incluidos los EnvironmentProviders) buscando un interceptor registrado.
  function registraInterceptor(nodo: unknown, vistos = new Set<unknown>()): boolean {
    if (!nodo || typeof nodo !== 'object' || vistos.has(nodo)) {
      return false;
    }
    vistos.add(nodo);
    if (Array.isArray(nodo)) {
      return nodo.some(n => registraInterceptor(n, vistos));
    }
    const objeto = nodo as Record<string, unknown>;
    if (objeto['provide'] === HTTP_INTERCEPTORS && objeto['useClass'] === AuthInterceptor) {
      return true;
    }
    return Object.values(objeto).some(v => registraInterceptor(v, vistos));
  }

  it('UT-FE-13 [AUT-1] appConfig registra el interceptor de autenticacion', () => {
    expect(registraInterceptor(appConfig.providers))
      .withContext('AuthInterceptor no esta en HTTP_INTERCEPTORS: nunca se ejecuta').toBeTrue();
  });

  it('UT-FE-14 [CON-2] el interceptor no envia el rol como cabecera (el cliente no decide su autorizacion)', () => {
    const req = interceptar({ id: 2, email: 'ana@corpo.com', rol: 'CLIENTE' });

    expect(req.headers.has('rol')).toBeFalse();
  });

  it('UT-FE-15 [AUT-1] el interceptor adjunta una credencial Authorization: Bearer', () => {
    const req = interceptar({ id: 2, email: 'ana@corpo.com', rol: 'CLIENTE', token: 'abc' });

    expect(req.headers.get('Authorization') ?? '').toMatch(/^Bearer\s.+/);
  });

  it('UT-FE-16 [Control] sin sesion el interceptor no agrega cabeceras', () => {
    const req = interceptar(null);

    expect(req.headers.keys().length).toBe(0);
  });
});
