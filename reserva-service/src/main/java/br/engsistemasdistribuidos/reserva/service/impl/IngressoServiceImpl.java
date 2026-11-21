package br.engsistemasdistribuidos.reserva.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.engsistemasdistribuidos.reserva.dto.response.IngressoResponse;
import br.engsistemasdistribuidos.reserva.repository.IngressoRepository;
import br.engsistemasdistribuidos.reserva.seguranca.UsuarioAutenticado;
import br.engsistemasdistribuidos.reserva.service.IngressoService;

@Service
@Transactional(readOnly = true)
public class IngressoServiceImpl implements IngressoService {

    private final IngressoRepository repository;

    public IngressoServiceImpl(IngressoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<IngressoResponse> listarMeus(UsuarioAutenticado usuario) {
        if (usuario.isGestor()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Operação permitida apenas para usuários comuns.");
        }
        return IngressoResponse.de(repository.listarDoUsuario(usuario.id()));
    }

    // Gestor vê qualquer ingresso; usuário comum, só os seus (404 para os de outra pessoa).
    @Override
    public IngressoResponse buscarPorId(UUID idIngresso, UsuarioAutenticado usuario) {
        return repository.buscarComReserva(idIngresso)
                .filter(ingresso -> usuario.isGestor() || ingresso.getReserva().getIdUsuario().equals(usuario.id()))
                .map(IngressoResponse::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingresso não encontrado."));
    }
}
