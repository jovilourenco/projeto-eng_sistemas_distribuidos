package br.engsistemasdistribuidos.reserva.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.engsistemasdistribuidos.reserva.dto.request.PagamentoRequest;
import br.engsistemasdistribuidos.reserva.dto.request.ReservaRequest;
import br.engsistemasdistribuidos.reserva.dto.response.ReservaResponse;
import br.engsistemasdistribuidos.reserva.model.Reserva;
import br.engsistemasdistribuidos.reserva.model.TipoIngresso;
import br.engsistemasdistribuidos.reserva.repository.ReservaRepository;
import br.engsistemasdistribuidos.reserva.seguranca.UsuarioAutenticado;
import br.engsistemasdistribuidos.reserva.service.ReservaApiService;
import br.engsistemasdistribuidos.reserva.service.ReservaService;

// Escritas sem @Transactional: o ReservaService controla as próprias transações (ver ReservaServiceImpl).
@Service
public class ReservaApiServiceImpl implements ReservaApiService {

    static final int TAMANHO_MAXIMO_CHAVE = 255;
    static final int MAXIMO_ASSENTOS = 4;

    private final ReservaRepository repository;
    private final ReservaService reservaService;

    public ReservaApiServiceImpl(ReservaRepository repository, ReservaService reservaService) {
        this.repository = repository;
        this.reservaService = reservaService;
    }

    @Override
    public Criada criar(ReservaRequest request, String chaveIdempotencia, UsuarioAutenticado usuario) {
        exigirUsuarioComum(usuario);
        validarChave(chaveIdempotencia);
        validar(request);

        TipoIngresso tipo = request.tipoIngresso() == null ? TipoIngresso.INTEIRA : request.tipoIngresso();
        ReservaService.Criacao criacao = reservaService.criarReserva(chaveIdempotencia, request.idEvento(),
                request.idAssentos(), usuario.id(), tipo);
        return new Criada(ReservaResponse.de(criacao.reserva()), criacao.nova());
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaResponse buscarPorId(UUID idReserva, UsuarioAutenticado usuario) {
        return ReservaResponse.de(recuperar(idReserva, usuario));
    }

    @Override
    public ReservaResponse pagar(UUID idReserva, PagamentoRequest request, String chaveIdempotencia,
                                 UsuarioAutenticado usuario) {
        // Só o dono paga: o gestor consulta e cancela, mas não paga a compra de outra pessoa.
        exigirUsuarioComum(usuario);
        validarChave(chaveIdempotencia);
        if (request == null || request.metodo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O método de pagamento é obrigatório (PIX, CARTAO_CREDITO ou BOLETO).");
        }
        Reserva reserva = recuperar(idReserva, usuario);
        return ReservaResponse.de(
                reservaService.solicitarPagamento(reserva, chaveIdempotencia, request.metodo(), request.simulacao()));
    }

    @Override
    public ReservaResponse cancelar(UUID idReserva, String chaveIdempotencia, UsuarioAutenticado usuario) {
        validarChave(chaveIdempotencia);
        Reserva reserva = recuperar(idReserva, usuario);
        return ReservaResponse.de(reservaService.cancelarReserva(reserva, chaveIdempotencia));
    }

    // Gestor acessa qualquer reserva; usuário comum, só as suas. Para o usuário comum, a reserva
    // de outra pessoa responde 404 (e não 403) para não revelar que aquele id existe.
    private Reserva recuperar(UUID idReserva, UsuarioAutenticado usuario) {
        return repository.findComIngressosById(idReserva)
                .filter(reserva -> usuario.isGestor() || reserva.getIdUsuario().equals(usuario.id()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva não encontrada."));
    }

    private static void exigirUsuarioComum(UsuarioAutenticado usuario) {
        if (usuario.isGestor()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Operação permitida apenas para usuários comuns.");
        }
    }

    // As escritas exigem o cabeçalho Idempotency-Key, gerado pelo cliente (ADR-002).
    private static void validarChave(String chave) {
        if (chave == null || chave.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O cabeçalho Idempotency-Key é obrigatório.");
        }
        if (chave.length() > TAMANHO_MAXIMO_CHAVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O cabeçalho Idempotency-Key deve ter no máximo " + TAMANHO_MAXIMO_CHAVE + " caracteres.");
        }
    }

    private static void validar(ReservaRequest request) {
        if (request == null || request.idEvento() == null || request.idEvento() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O evento é obrigatório.");
        }
        List<Integer> assentos = request.idAssentos();
        if (assentos == null || assentos.isEmpty() || assentos.size() > MAXIMO_ASSENTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe de 1 a " + MAXIMO_ASSENTOS + " assentos.");
        }
        if (assentos.stream().anyMatch(id -> id == null || id < 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assento inválido.");
        }
        if (new HashSet<>(assentos).size() != assentos.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assento repetido na mesma reserva.");
        }
    }
}
