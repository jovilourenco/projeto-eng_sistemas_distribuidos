package br.engsistemasdistribuidos.reserva.seguranca;

// Usuário da requisição. Hoje vem de cabeçalhos simulados; depois virá das claims do JWT (Keycloak).
public record UsuarioAutenticado(Integer id, Perfil perfil) {

    public boolean isGestor() {
        return perfil == Perfil.GESTOR_EVENTO;
    }
}
