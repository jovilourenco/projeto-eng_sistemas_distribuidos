package br.engsistemasdistribuidos.reserva.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.engsistemasdistribuidos.reserva.dto.request.IngressoRequest;
import br.engsistemasdistribuidos.reserva.dto.response.IngressoResponse;
import br.engsistemasdistribuidos.reserva.mapper.IngressoMapper;
import br.engsistemasdistribuidos.reserva.model.Ingresso;
import br.engsistemasdistribuidos.reserva.repository.IngressoRepository;
import br.engsistemasdistribuidos.reserva.seguranca.UsuarioAutenticado;
import br.engsistemasdistribuidos.reserva.service.IngressoService;
import br.engsistemasdistribuidos.reserva.service.ReservaService;

// Escritas sem @Transactional: o ReservaService controla as próprias transações (ver ReservaServiceImpl).
@Service
public class IngressoServiceImpl implements IngressoService {

    static final int TAMANHO_MAXIMO_CHAVE = 255;

    private final IngressoRepository repository;
    private final ReservaService reservaService;
    private final IngressoMapper mapper;

    public IngressoServiceImpl(IngressoRepository repository,
                               ReservaService reservaService,
                               IngressoMapper mapper) {
        this.repository = repository;
        this.reservaService = reservaService;
        this.mapper = mapper;
    }

    @Override
    public IngressoResponse criar(IngressoRequest request, String chaveIdempotencia, UsuarioAutenticado usuario) {
        exigirUsuarioComum(usuario);
        validarChave(chaveIdempotencia);
        validar(request);

        Ingresso ingresso = reservaService.criarReserva(chaveIdempotencia,
                request.getIdEvento(), request.getIdAssento(), usuario.id(), request.getTipoIngresso());
        return mapper.toResponse(ingresso);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngressoResponse> listarMeus(UsuarioAutenticado usuario) {
        exigirUsuarioComum(usuario);
        return mapper.toResponse(repository.findByIdUsuarioOrderByDataReservaDesc(usuario.id()));
    }

    @Override
    @Transactional(readOnly = true)
    public IngressoResponse buscarPorId(UUID idIngresso, UsuarioAutenticado usuario) {
        return mapper.toResponse(recuperarIngresso(idIngresso, usuario));
    }

    @Override
    public IngressoResponse cancelar(UUID idIngresso, String chaveIdempotencia, UsuarioAutenticado usuario) {
        validarChave(chaveIdempotencia);
        Ingresso ingresso = recuperarIngresso(idIngresso, usuario);
        return mapper.toResponse(reservaService.cancelarReserva(ingresso, chaveIdempotencia));
    }

    // Gestor acessa qualquer ingresso; usuário comum, só os seus. Para o usuário comum, o ingresso
    // de outra pessoa responde 404 (e não 403) para não revelar que aquele id existe.
    private Ingresso recuperarIngresso(UUID idIngresso, UsuarioAutenticado usuario) {
        return repository.findById(idIngresso)
                .filter(ingresso -> usuario.isGestor() || ingresso.getIdUsuario().equals(usuario.id()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingresso não encontrado."));
    }

    private void exigirUsuarioComum(UsuarioAutenticado usuario) {
        if (usuario.isGestor()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Operação permitida apenas para usuários comuns.");
        }
    }

    // As escritas exigem o cabeçalho Idempotency-Key, gerado pelo cliente (ADR-002).
    private void validarChave(String chave) {
        if (chave == null || chave.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O cabeçalho Idempotency-Key é obrigatório.");
        }
        if (chave.length() > TAMANHO_MAXIMO_CHAVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O cabeçalho Idempotency-Key deve ter no máximo " + TAMANHO_MAXIMO_CHAVE + " caracteres.");
        }
    }

    private void validar(IngressoRequest request) {
        if (request.getIdEvento() == null || request.getIdEvento() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O evento é obrigatório.");
        }
        if (request.getIdAssento() == null || request.getIdAssento() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O assento é obrigatório.");
        }
        if (request.getTipoIngresso() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O tipo do ingresso é obrigatório (INTEIRA ou ESTUDANTE).");
        }
    }
}
