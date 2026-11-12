-- Log de auditoria compartilhado por todos os módulos (P6, ADR-0002): quem, quando, operação e resultado.
-- Somente inserção: alterações e exclusões são barradas no banco, independentemente da aplicação.

CREATE TABLE auditoria (
	id UUID NOT NULL,
	ocorrido_em TIMESTAMP WITH TIME ZONE NOT NULL,
	-- Sem FK: o registro precisa sobreviver ao usuário. Nulo quando quem agiu é desconhecido.
	usuario_id UUID,
	operacao VARCHAR(60) NOT NULL,
	resultado VARCHAR(10) NOT NULL,
	recurso_id UUID,
	detalhe VARCHAR(255),
	CONSTRAINT auditoria_pk PRIMARY KEY (id),
	CONSTRAINT auditoria_resultado_ck CHECK (resultado IN ('SUCESSO', 'FALHA')),
	CONSTRAINT auditoria_operacao_ck CHECK (operacao ~ '^[A-Z][A-Z0-9_]*$')
);

CREATE INDEX auditoria_usuario_idx ON auditoria (usuario_id, ocorrido_em);
CREATE INDEX auditoria_operacao_idx ON auditoria (operacao, ocorrido_em);

CREATE FUNCTION impedir_alteracao_auditoria() RETURNS trigger AS $$
BEGIN
	RAISE EXCEPTION 'A auditoria aceita apenas inserções' USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER auditoria_somente_insercao_trg
	BEFORE UPDATE OR DELETE ON auditoria
	FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_auditoria();

CREATE TRIGGER auditoria_sem_truncate_trg
	BEFORE TRUNCATE ON auditoria
	FOR EACH STATEMENT EXECUTE FUNCTION impedir_alteracao_auditoria();
