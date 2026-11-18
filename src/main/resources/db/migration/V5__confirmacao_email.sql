-- Confirmação de e-mail (ID-08): o login só é liberado depois que o usuário prova ser dono do endereço.

ALTER TABLE usuario ADD COLUMN email_confirmado_em TIMESTAMP WITH TIME ZONE;

-- Quem já usava o sistema antes da confirmação não fica trancado.
UPDATE usuario SET email_confirmado_em = criado_em;

-- Usada pela limpeza das contas que nunca confirmaram.
CREATE INDEX usuario_nao_confirmado_idx ON usuario (criado_em) WHERE email_confirmado_em IS NULL;

-- Mesmo modelo da recuperação de senha: só o SHA-256 do token é gravado, com validade e uso único.
CREATE TABLE token_confirmacao_email (
	id UUID NOT NULL,
	usuario_id UUID NOT NULL,
	token_hash VARCHAR(64) NOT NULL,
	expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
	usado_em TIMESTAMP WITH TIME ZONE,
	criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
	CONSTRAINT token_confirmacao_email_pk PRIMARY KEY (id),
	CONSTRAINT token_confirmacao_email_hash_uk UNIQUE (token_hash),
	CONSTRAINT token_confirmacao_email_hash_ck CHECK (token_hash ~ '^[0-9a-f]{64}$'),
	CONSTRAINT token_confirmacao_email_usuario_fk FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
);

CREATE INDEX token_confirmacao_email_usuario_idx ON token_confirmacao_email (usuario_id, criado_em);
