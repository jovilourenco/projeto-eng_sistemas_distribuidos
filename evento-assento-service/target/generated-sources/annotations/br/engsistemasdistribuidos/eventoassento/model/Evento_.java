package br.engsistemasdistribuidos.eventoassento.model;

import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.LocalDateTime;
import javax.annotation.processing.Generated;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Evento.class)
public abstract class Evento_ {

	public static volatile SingularAttribute<Evento, LocalDateTime> dataEvento;
	public static volatile SingularAttribute<Evento, LocalDateTime> dataAtualizacao;
	public static volatile SingularAttribute<Evento, Integer> idUsuarioCadastro;
	public static volatile SingularAttribute<Evento, String> titulo;
	public static volatile SingularAttribute<Evento, Integer> id;
	public static volatile SingularAttribute<Evento, Integer> idUsuarioAtualizacao;
	public static volatile SingularAttribute<Evento, LocalDateTime> dataCadastro;
	public static volatile SingularAttribute<Evento, String> descricao;

	public static final String DATA_EVENTO = "dataEvento";
	public static final String DATA_ATUALIZACAO = "dataAtualizacao";
	public static final String ID_USUARIO_CADASTRO = "idUsuarioCadastro";
	public static final String TITULO = "titulo";
	public static final String ID = "id";
	public static final String ID_USUARIO_ATUALIZACAO = "idUsuarioAtualizacao";
	public static final String DATA_CADASTRO = "dataCadastro";
	public static final String DESCRICAO = "descricao";

}

