package br.engsistemasdistribuidos.reserva.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.engsistemasdistribuidos.reserva.seguranca.UsuarioAutenticadoArgumentResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UsuarioAutenticadoArgumentResolver usuarioResolver;

    public WebConfig(UsuarioAutenticadoArgumentResolver usuarioResolver) {
        this.usuarioResolver = usuarioResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(usuarioResolver);
    }
}
