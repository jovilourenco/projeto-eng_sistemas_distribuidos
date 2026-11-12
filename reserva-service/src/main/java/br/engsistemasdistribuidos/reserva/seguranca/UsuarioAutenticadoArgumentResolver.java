package br.engsistemasdistribuidos.reserva.seguranca;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

/**
 * MOCK de autenticação: injeta um {@link UsuarioAutenticado} nos controllers a partir de cabeçalhos.
 * Sem os cabeçalhos, assume o usuário 1 com perfil USUARIO.
 *
 * Quando o Keycloak for integrado, basta trocar esta classe por uma que leia o JWT;
 * controllers e services não mudam.
 */
@Component
public class UsuarioAutenticadoArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_ID = "X-Usuario-Id";
    public static final String HEADER_PERFIL = "X-Usuario-Perfil";

    private static final int ID_PADRAO = 1;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return UsuarioAutenticado.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return new UsuarioAutenticado(
                lerId(webRequest.getHeader(HEADER_ID)),
                lerPerfil(webRequest.getHeader(HEADER_PERFIL)));
    }

    private Integer lerId(String valor) {
        if (valor == null || valor.isBlank()) {
            return ID_PADRAO;
        }
        try {
            int id = Integer.parseInt(valor.trim());
            if (id < 1) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cabeçalho " + HEADER_ID + " inválido.");
        }
    }

    private Perfil lerPerfil(String valor) {
        if (valor == null || valor.isBlank()) {
            return Perfil.USUARIO;
        }
        try {
            return Perfil.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cabeçalho " + HEADER_PERFIL + " inválido.");
        }
    }
}
