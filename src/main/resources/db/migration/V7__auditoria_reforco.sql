-- Reforço da auditoria (ADR-0002): origem da requisição, ID de correlação e hash encadeado.
-- Só acrescenta colunas anuláveis: nenhuma linha antiga é alterada e nenhuma trigger é desligada.

-- 1. Origem e correlação. IP e user-agent são dados pessoais: nunca vão para logs nem para respostas da API, e o
--    prazo de retenção está pendente (P10). Nulos quando o evento não vem de uma requisição HTTP.
ALTER TABLE auditoria
	ADD COLUMN ip_origem INET,
	ADD COLUMN agente_usuario VARCHAR(255),
	ADD COLUMN requisicao_id UUID,
	-- 2. Cadeia: posição, hash do elo anterior e hash da própria linha (SHA-256 em hexadecimal minúsculo).
	--    Nulos só nas linhas anteriores a esta migration, cobertas pelo registro de gênese.
	ADD COLUMN seq BIGINT,
	ADD COLUMN hash_anterior CHAR(64),
	ADD COLUMN hash CHAR(64),
	ADD CONSTRAINT auditoria_seq_uk UNIQUE (seq);

CREATE INDEX auditoria_requisicao_idx ON auditoria (requisicao_id);

-- Formato canônico de um campo: "<tamanho em caracteres>:<valor>;" e "-;" para nulo. O prefixo de tamanho evita
-- ambiguidade quando o valor contém ":" ou ";".
CREATE FUNCTION auditoria_campo(valor TEXT) RETURNS TEXT AS $$
	SELECT CASE WHEN valor IS NULL THEN '-;' ELSE length(valor) || ':' || valor || ';' END;
$$ LANGUAGE sql IMMUTABLE;

-- Serialização canônica de uma linha, nesta ordem: seq, id, ocorrido_em (UTC, microssegundos), usuario_id, operacao,
-- resultado, recurso_id, detalhe, ip_origem (sem máscara), agente_usuario, requisicao_id, hash_anterior.
-- UUIDs em minúsculas com hífens. O verificador em Java (AuditChainVerifier) reproduz este formato.
CREATE FUNCTION auditoria_serializar(r auditoria) RETURNS TEXT AS $$
	SELECT auditoria_campo(r.seq::TEXT)
		|| auditoria_campo(r.id::TEXT)
		|| auditoria_campo(to_char(r.ocorrido_em AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'))
		|| auditoria_campo(r.usuario_id::TEXT)
		|| auditoria_campo(r.operacao)
		|| auditoria_campo(r.resultado)
		|| auditoria_campo(r.recurso_id::TEXT)
		|| auditoria_campo(r.detalhe)
		|| auditoria_campo(host(r.ip_origem))
		|| auditoria_campo(r.agente_usuario)
		|| auditoria_campo(r.requisicao_id::TEXT)
		|| auditoria_campo(r.hash_anterior);
$$ LANGUAGE sql IMMUTABLE;

CREATE FUNCTION auditoria_sha256(texto TEXT) RETURNS CHAR(64) AS $$
	SELECT encode(sha256(convert_to(texto, 'UTF8')), 'hex');
$$ LANGUAGE sql IMMUTABLE;

-- Cabeça da cadeia: uma única linha com a última posição e o último hash. Travar essa linha serializa os inserts
-- até o commit, então dois elos nunca têm o mesmo antecessor.
CREATE TABLE auditoria_cadeia (
	id SMALLINT NOT NULL,
	ultima_seq BIGINT NOT NULL,
	ultimo_hash CHAR(64) NOT NULL,
	CONSTRAINT auditoria_cadeia_pk PRIMARY KEY (id),
	CONSTRAINT auditoria_cadeia_unica_ck CHECK (id = 1)
);

CREATE TRIGGER auditoria_cadeia_sem_exclusao_trg
	BEFORE DELETE ON auditoria_cadeia
	FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_auditoria();

CREATE TRIGGER auditoria_cadeia_sem_truncate_trg
	BEFORE TRUNCATE ON auditoria_cadeia
	FOR EACH STATEMENT EXECUTE FUNCTION impedir_alteracao_auditoria();

-- Linhas antigas (sem seq): o resumo delas, na ordem (ocorrido_em, id), vira o hash anterior da gênese. Alterar uma
-- delas muda o resumo e quebra a verificação, sem precisar de UPDATE nelas.
INSERT INTO auditoria_cadeia (id, ultima_seq, ultimo_hash)
SELECT 1, 0, auditoria_sha256(coalesce(string_agg(auditoria_serializar(a), '' ORDER BY a.ocorrido_em, a.id), ''))
FROM auditoria a;

-- Calcula seq, hash_anterior e hash de toda linha nova, mesmo inserida fora da aplicação. Valores enviados no INSERT
-- para essas colunas são descartados.
CREATE FUNCTION encadear_auditoria() RETURNS trigger AS $$
DECLARE
	cabeca auditoria_cadeia%ROWTYPE;
BEGIN
	SELECT * INTO cabeca FROM auditoria_cadeia WHERE id = 1 FOR UPDATE;
	IF NOT FOUND THEN
		RAISE EXCEPTION 'Cabeça da cadeia de auditoria ausente' USING ERRCODE = 'restrict_violation';
	END IF;
	NEW.seq := cabeca.ultima_seq + 1;
	NEW.hash_anterior := cabeca.ultimo_hash;
	NEW.hash := auditoria_sha256(auditoria_serializar(NEW));
	UPDATE auditoria_cadeia SET ultima_seq = NEW.seq, ultimo_hash = NEW.hash WHERE id = 1;
	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER auditoria_encadear_trg
	BEFORE INSERT ON auditoria
	FOR EACH ROW EXECUTE FUNCTION encadear_auditoria();

-- Gênese: primeiro elo (seq 1). O detalhe guarda quantas linhas antigas o resumo cobre.
INSERT INTO auditoria (id, ocorrido_em, operacao, resultado, detalhe)
SELECT gen_random_uuid(), date_trunc('milliseconds', now()), 'GENESE_CADEIA', 'SUCESSO', 'LEGADO:' || count(*)
FROM auditoria;
