package br.engsistemasdistribuidos.reserva.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.engsistemasdistribuidos.reserva.dto.request.IngressoRequest;
import br.engsistemasdistribuidos.reserva.mapper.IngressoMapper;
import br.engsistemasdistribuidos.reserva.model.Ingresso;
import br.engsistemasdistribuidos.reserva.model.StatusIngresso;
import br.engsistemasdistribuidos.reserva.model.TipoIngresso;
import br.engsistemasdistribuidos.reserva.repository.IngressoRepository;
import br.engsistemasdistribuidos.reserva.seguranca.Perfil;
import br.engsistemasdistribuidos.reserva.seguranca.UsuarioAutenticado;
import br.engsistemasdistribuidos.reserva.service.ReservaService;
import br.engsistemasdistribuidos.reserva.service.impl.IngressoServiceImpl;

@ExtendWith(MockitoExtension.class)
public class IngressoServiceImplTests {

    private static final UsuarioAutenticado DONO = new UsuarioAutenticado(20, Perfil.USUARIO);
    private static final UsuarioAutenticado OUTRO = new UsuarioAutenticado(21, Perfil.USUARIO);
    private static final UsuarioAutenticado GESTOR = new UsuarioAutenticado(1, Perfil.GESTOR_EVENTO);
    private static final UUID ID = UUID.randomUUID();
    private static final String CHAVE = "chave-1";

    @Mock
    private IngressoRepository repository;

    @Mock
    private ReservaService reservaService;

    @Mock
    private IngressoMapper mapper;

    private IngressoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new IngressoServiceImpl(repository, reservaService, mapper);
    }

    private Ingresso ingressoDoDono() {
        Ingresso ingresso = new Ingresso();
        ingresso.setId(ID);
        ingresso.setIdUsuario(DONO.id());
        ingresso.setStatus(StatusIngresso.PENDENTE_PAGAMENTO);
        return ingresso;
    }

    private IngressoRequest request(Integer idEvento, Integer idAssento, TipoIngresso tipo) {
        IngressoRequest request = new IngressoRequest();
        request.setIdEvento(idEvento);
        request.setIdAssento(idAssento);
        request.setTipoIngresso(tipo);
        return request;
    }

    private HttpStatus status(ResponseStatusException ex) {
        return HttpStatus.valueOf(ex.getStatusCode().value());
    }

    // ------------------------------------------------------------------ criar

    @Test
    void criar_usaChaveEIdDoUsuarioAutenticado() {
        service.criar(request(1, 10, TipoIngresso.ESTUDANTE), CHAVE, DONO);

        verify(reservaService).criarReserva(CHAVE, 1, 10, DONO.id(), TipoIngresso.ESTUDANTE);
    }

    @Test
    void criar_semChave_retorna400() {
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.criar(request(1, 10, TipoIngresso.INTEIRA), null, DONO));

        assertThat(status(ex)).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(reservaService);
    }

    @Test
    void criar_chaveEmBranco_retorna400() {
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.criar(request(1, 10, TipoIngresso.INTEIRA), "   ", DONO));

        assertThat(status(ex)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void criar_chaveLongaDemais_retorna400() {
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.criar(request(1, 10, TipoIngresso.INTEIRA), "x".repeat(256), DONO));

        assertThat(status(ex)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void criar_gestor_retorna403() {
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.criar(request(1, 10, TipoIngresso.INTEIRA), CHAVE, GESTOR));

        assertThat(status(ex)).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(reservaService);
    }

    @Test
    void criar_semTipo_retorna400() {
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.criar(request(1, 10, null), CHAVE, DONO));

        assertThat(status(ex)).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(reservaService);
    }

    @Test
    void criar_semAssento_retorna400() {
        var ex = assertThrows(ResponseStatusException.class,
                () -> service.criar(request(1, null, TipoIngresso.INTEIRA), CHAVE, DONO));

        assertThat(status(ex)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------ consultas

    @Test
    void listarMeus_gestor_retorna403() {
        var ex = assertThrows(ResponseStatusException.class, () -> service.listarMeus(GESTOR));

        assertThat(status(ex)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void buscarPorId_deOutroUsuario_retorna404() {
        when(repository.findById(ID)).thenReturn(Optional.of(ingressoDoDono()));

        var ex = assertThrows(ResponseStatusException.class, () -> service.buscarPorId(ID, OUTRO));

        assertThat(status(ex)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void buscarPorId_gestorVeQualquerIngresso() {
        Ingresso ingresso = ingressoDoDono();
        when(repository.findById(ID)).thenReturn(Optional.of(ingresso));

        service.buscarPorId(ID, GESTOR);

        verify(mapper).toResponse(ingresso);
    }

    // ------------------------------------------------------------------ cancelar

    @Test
    void cancelar_doDono_delegaComAChave() {
        Ingresso ingresso = ingressoDoDono();
        when(repository.findById(ID)).thenReturn(Optional.of(ingresso));

        service.cancelar(ID, CHAVE, DONO);

        verify(reservaService).cancelarReserva(ingresso, CHAVE);
    }

    @Test
    void cancelar_semChave_retorna400() {
        var ex = assertThrows(ResponseStatusException.class, () -> service.cancelar(ID, null, DONO));

        assertThat(status(ex)).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(repository, reservaService);
    }

    @Test
    void cancelar_deOutroUsuario_retorna404() {
        when(repository.findById(ID)).thenReturn(Optional.of(ingressoDoDono()));

        var ex = assertThrows(ResponseStatusException.class, () -> service.cancelar(ID, CHAVE, OUTRO));

        assertThat(status(ex)).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(reservaService);
    }
}
