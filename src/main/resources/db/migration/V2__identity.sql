-- Módulo identity: usuários (Apêndice A) e tokens de recuperação de senha (REQ 02).

CREATE TABLE usuario (
	id UUID NOT NULL,
	nome_completo VARCHAR(150) NOT NULL,
	email VARCHAR(254) NOT NULL,
	senha_hash VARCHAR(255) NOT NULL,
	cpf VARCHAR(11) NOT NULL,
	tipo VARCHAR(20) NOT NULL,
	oab_numero VARCHAR(6),
	oab_uf VARCHAR(2),
	criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
	CONSTRAINT usuario_pk PRIMARY KEY (id),
	CONSTRAINT usuario_email_uk UNIQUE (email),
	CONSTRAINT usuario_cpf_uk UNIQUE (cpf),
	CONSTRAINT usuario_email_minusculo_ck CHECK (email = LOWER(email)),
	CONSTRAINT usuario_cpf_formato_ck CHECK (cpf ~ '^[0-9]{11}$'),
	CONSTRAINT usuario_tipo_ck CHECK (tipo IN ('ADVOGADO', 'COMUM')),
	-- OAB obrigatória só para advogado. Um novo tipo (EMPRESA, D1) entra aqui sem reescrever a tabela.
	-- Os IS NOT NULL são necessários: com NULL a regex dá NULL, e um CHECK com resultado NULL é aceito.
	CONSTRAINT usuario_oab_ck CHECK (
		(tipo = 'ADVOGADO' AND oab_numero IS NOT NULL AND oab_uf IS NOT NULL
			AND oab_numero ~ '^[0-9]{1,6}$' AND oab_uf ~ '^[A-Z]{2}$')
		OR (tipo <> 'ADVOGADO' AND oab_numero IS NULL AND oab_uf IS NULL)
	)
);

CREATE TABLE token_recuperacao_senha (
	id UUID NOT NULL,
	usuario_id UUID NOT NULL,
	token_hash VARCHAR(64) NOT NULL,
	expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
	usado_em TIMESTAMP WITH TIME ZONE,
	criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
	CONSTRAINT token_recuperacao_senha_pk PRIMARY KEY (id),
	CONSTRAINT token_recuperacao_senha_hash_uk UNIQUE (token_hash),
	CONSTRAINT token_recuperacao_senha_hash_ck CHECK (token_hash ~ '^[0-9a-f]{64}$'),
	CONSTRAINT token_recuperacao_senha_usuario_fk FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
);

CREATE INDEX token_recuperacao_senha_usuario_idx ON token_recuperacao_senha (usuario_id);
