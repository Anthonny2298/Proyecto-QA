package com.corpomotriz.repuestos.seguridad.servicio;

import com.corpomotriz.repuestos.dto.UsuarioDTO;
import com.corpomotriz.repuestos.dto.crear.UsuarioCrearDTO;
import com.corpomotriz.repuestos.model.Usuario;
import com.corpomotriz.repuestos.repository.UsuarioRepository;
import com.corpomotriz.repuestos.service.UsuarioService;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UsuarioService. HU Crear Perfil (ERS 1.1.3) y Editar Perfil (Brief v1.2).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UsuarioService - seguridad")
class UsuarioServiceSeguridadTest {

    private static final String CLAVE = "Secreta1";

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UsuarioService usuarioService;

    @BeforeEach
    void guardarDevuelveLaEntidadConId() {
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(1L);
            }
            return u;
        });
    }

    private static UsuarioCrearDTO dto(String email, String clave, String rol) {
        return UsuarioCrearDTO.builder().nombre("Ana Perez").email(email).contrasena(clave).rol(rol).build();
    }

    private static Usuario existente(long id, String email, String rol) {
        return Usuario.builder().id(id).nombre("Ana").email(email).contrasena("Vieja123").rol(rol)
                .fechaCreacion(LocalDateTime.of(2025, 1, 1, 0, 0)).build();
    }

    private Usuario usuarioGuardado() {
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("UT-SRV-01 [CON-4] Crear perfil guarda la contrasena cifrada, no en texto plano")
    void crearPerfilCifraContrasena() {
        usuarioService.createUser(dto("ana@corpo.com", CLAVE, "CLIENTE"));

        assertThat(usuarioGuardado().getContrasena()).isNotEqualTo(CLAVE);
    }

    @Test
    @DisplayName("UT-SRV-02 [INT-4] Crear perfil asigna CLIENTE aunque se pida ADMINISTRADOR")
    void crearPerfilFuerzaRolCliente() {
        usuarioService.createUser(dto("ana@corpo.com", CLAVE, "ADMINISTRADOR"));

        assertThat(usuarioGuardado().getRol()).isEqualTo("CLIENTE");
    }

    @Test
    @DisplayName("UT-SRV-03 [ACC-1] Crear perfil con un correo ya registrado se rechaza y no guarda")
    void crearPerfilCorreoDuplicado() {
        when(usuarioRepository.findByEmail("ana@corpo.com"))
                .thenReturn(Optional.of(existente(2L, "ana@corpo.com", "CLIENTE")));

        assertThatThrownBy(() -> usuarioService.createUser(dto("ana@corpo.com", CLAVE, "CLIENTE")))
                .isInstanceOf(RuntimeException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-SRV-04 [INT-4] Editar perfil no permite escalar el rol a ADMINISTRADOR")
    void editarPerfilNoEscalaRol() {
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(existente(1L, "ana@corpo.com", "CLIENTE")));

        usuarioService.updateUser(1, dto("ana@corpo.com", CLAVE, "ADMINISTRADOR"));

        assertThat(usuarioGuardado().getRol()).isEqualTo("CLIENTE");
    }

    @Test
    @DisplayName("UT-SRV-05 [CON-4] Editar perfil guarda la nueva contrasena cifrada")
    void editarPerfilCifraContrasena() {
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(existente(1L, "ana@corpo.com", "CLIENTE")));

        usuarioService.updateUser(1, dto("ana@corpo.com", CLAVE, "CLIENTE"));

        assertThat(usuarioGuardado().getContrasena()).isNotEqualTo(CLAVE);
    }

    @Test
    @DisplayName("UT-SRV-06 [ACC-1] Editar perfil con el correo de otro usuario se rechaza")
    void editarPerfilCorreoDeOtro() {
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(existente(1L, "ana@corpo.com", "CLIENTE")));
        when(usuarioRepository.findByEmail("luis@corpo.com"))
                .thenReturn(Optional.of(existente(2L, "luis@corpo.com", "CLIENTE")));

        assertThatThrownBy(() -> usuarioService.updateUser(1, dto("luis@corpo.com", CLAVE, "CLIENTE")))
                .isInstanceOf(RuntimeException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-SRV-07 [CON-5] El DTO de salida de usuario no tiene campo de contrasena")
    void dtoSalidaSinContrasena() {
        boolean tieneContrasena = Arrays.stream(UsuarioDTO.class.getDeclaredFields())
                .anyMatch(f -> f.getName().toLowerCase().contains("contrasena"));

        assertThat(tieneContrasena).isFalse();
    }

    @Test
    @DisplayName("UT-SRV-08 [ACC-1] El correo duplicado se detecta sin importar mayusculas")
    void correoDuplicadoSinDistinguirMayusculas() {
        when(usuarioRepository.findByEmail("ana@corpo.com"))
                .thenReturn(Optional.of(existente(2L, "ana@corpo.com", "CLIENTE")));

        assertThatThrownBy(() -> usuarioService.createUser(dto("ANA@CORPO.COM", CLAVE, "CLIENTE")))
                .as("ANA@CORPO.COM y ana@corpo.com son la misma identidad").isInstanceOf(RuntimeException.class);
    }
}
