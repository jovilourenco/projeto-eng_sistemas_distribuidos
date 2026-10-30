package br.engsistemasdistribuidos.reserva.service;

public interface ReservaService {
    
    /**
     * Tenta reservar um assento para um usuário usando lock do Redis.
     * 
     * @param idAssento Identificador do assento
     * @param idUsuario Identificador do usuário que está comprando
     */
    void criarReserva(Integer idAssento, Integer idUsuario);
    
}