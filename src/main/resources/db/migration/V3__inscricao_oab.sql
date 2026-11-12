-- Inscrições na OAB em tabela própria: um advogado pode ter várias (principal, suplementares, estágio).

CREATE TABLE inscricao_oab (
	id UUID NOT NULL,
	usuario_id UUID NOT NULL,
	numero VARCHAR(6) NOT NULL,
	uf VARCHAR(2) NOT NULL,
	tipo VARCHAR(20) NOT NULL,
	criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
	CONSTRAINT inscricao_oab_pk PRIMARY KEY (id),
	CONSTRAINT inscricao_oab_usuario_fk FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
	CONSTRAINT uk_oab_numero_uf_tipo UNIQUE (numero, uf, tipo),
	-- Sem zeros à esquerda: "00123" e "123" são a mesma inscrição e não podem burlar a unicidade.
	CONSTRAINT inscricao_oab_numero_ck CHECK (numero ~ '^[1-9][0-9]{0,5}$'),
	CONSTRAINT inscricao_oab_uf_ck CHECK (uf IN ('AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS',
		'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO')),
	CONSTRAINT inscricao_oab_tipo_ck CHECK (tipo IN ('PRINCIPAL', 'SUPLEMENTAR', 'ESTAGIARIO'))
);

CREATE INDEX inscricao_oab_usuario_idx ON inscricao_oab (usuario_id);

-- Um advogado tem no máximo uma inscrição principal.
CREATE UNIQUE INDEX uk_oab_uma_principal_por_usuario ON inscricao_oab (usuario_id) WHERE tipo = 'PRINCIPAL';

INSERT INTO inscricao_oab (id, usuario_id, numero, uf, tipo, criado_em)
SELECT gen_random_uuid(), id, LTRIM(oab_numero, '0'), oab_uf, 'PRINCIPAL', criado_em
FROM usuario
WHERE tipo = 'ADVOGADO';

ALTER TABLE usuario
	DROP CONSTRAINT usuario_oab_ck,
	DROP COLUMN oab_numero,
	DROP COLUMN oab_uf;

-- Substitui o antigo usuario_oab_ck, que não alcança outra tabela: advogado precisa de ao menos uma inscrição e os
-- demais tipos não podem ter nenhuma. Roda no commit, para permitir gravar o usuário e as inscrições na mesma transação.
CREATE FUNCTION verificar_inscricoes_oab_do_usuario() RETURNS trigger AS $$
DECLARE
	alvo UUID;
	tipo_usuario VARCHAR(20);
	total INTEGER;
BEGIN
	IF TG_TABLE_NAME = 'usuario' THEN
		alvo := NEW.id;
	ELSIF TG_OP = 'DELETE' THEN
		alvo := OLD.usuario_id;
	ELSE
		alvo := NEW.usuario_id;
	END IF;

	SELECT tipo INTO tipo_usuario FROM usuario WHERE id = alvo;
	IF NOT FOUND THEN
		RETURN NULL;
	END IF;

	SELECT COUNT(*) INTO total FROM inscricao_oab WHERE usuario_id = alvo;
	IF (tipo_usuario = 'ADVOGADO' AND total = 0) OR (tipo_usuario <> 'ADVOGADO' AND total > 0) THEN
		RAISE EXCEPTION 'Inscrições OAB incompatíveis com o tipo do usuário'
			USING ERRCODE = '23514', CONSTRAINT = 'usuario_inscricoes_oab_ck';
	END IF;
	RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER usuario_inscricoes_oab_trg
	AFTER INSERT OR UPDATE OF tipo ON usuario
	DEFERRABLE INITIALLY DEFERRED
	FOR EACH ROW EXECUTE FUNCTION verificar_inscricoes_oab_do_usuario();

CREATE CONSTRAINT TRIGGER inscricao_oab_usuario_trg
	AFTER INSERT OR UPDATE OR DELETE ON inscricao_oab
	DEFERRABLE INITIALLY DEFERRED
	FOR EACH ROW EXECUTE FUNCTION verificar_inscricoes_oab_do_usuario();
