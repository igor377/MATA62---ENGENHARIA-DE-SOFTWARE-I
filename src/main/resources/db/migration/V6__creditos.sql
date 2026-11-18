-- Créditos (CRE-01, CRE-02 e CRE-03): pacotes, saldo, movimentações e pedidos de compra.
-- Mudanças de valores entram por nova migration; pedidos antigos guardam uma cópia do pacote e não mudam.

CREATE TABLE pacote_credito (
	id UUID NOT NULL,
	codigo VARCHAR(20) NOT NULL,
	nome VARCHAR(60) NOT NULL,
	creditos INTEGER NOT NULL,
	valor_por_credito NUMERIC(10, 2) NOT NULL,
	beneficios VARCHAR(255) NOT NULL,
	ativo BOOLEAN NOT NULL,
	ordem INTEGER NOT NULL,
	CONSTRAINT pacote_credito_pk PRIMARY KEY (id),
	CONSTRAINT pacote_credito_codigo_uk UNIQUE (codigo),
	CONSTRAINT pacote_credito_codigo_ck CHECK (codigo ~ '^[A-Z][A-Z_]*$'),
	CONSTRAINT pacote_credito_creditos_ck CHECK (creditos > 0),
	CONSTRAINT pacote_credito_valor_ck CHECK (valor_por_credito > 0)
);

-- Pacotes da decisão D2. Valores provisórios (P7).
INSERT INTO pacote_credito (id, codigo, nome, creditos, valor_por_credito, beneficios, ativo, ordem) VALUES
	(gen_random_uuid(), 'BASICO', 'Básico', 10, 5.00, 'Ideal para registros pontuais', TRUE, 1),
	(gen_random_uuid(), 'MEDIO', 'Médio', 50, 4.50, '10% de desconto por crédito', TRUE, 2),
	(gen_random_uuid(), 'PREMIUM', 'Premium', 100, 4.00, '20% de desconto por crédito', TRUE, 3);

-- Uma linha por usuário. O UPDATE condicional nesta linha serializa os débitos simultâneos; o CHECK é a última
-- barreira contra saldo negativo. Sem FK para usuario: credits não conhece identity.
CREATE TABLE saldo_credito (
	usuario_id UUID NOT NULL,
	saldo INTEGER NOT NULL,
	atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
	CONSTRAINT saldo_credito_pk PRIMARY KEY (usuario_id),
	CONSTRAINT saldo_credito_nao_negativo_ck CHECK (saldo >= 0)
);

-- Extrato somente de inserção. A chave de idempotência impede aplicar a mesma operação duas vezes.
CREATE TABLE movimentacao_credito (
	id UUID NOT NULL,
	usuario_id UUID NOT NULL,
	tipo VARCHAR(10) NOT NULL,
	quantidade INTEGER NOT NULL,
	saldo_apos INTEGER NOT NULL,
	origem VARCHAR(20) NOT NULL,
	referencia_id UUID,
	chave_idempotencia VARCHAR(150) NOT NULL,
	criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
	CONSTRAINT movimentacao_credito_pk PRIMARY KEY (id),
	CONSTRAINT movimentacao_credito_chave_uk UNIQUE (chave_idempotencia),
	CONSTRAINT movimentacao_credito_tipo_ck CHECK (tipo IN ('CREDITO', 'DEBITO')),
	CONSTRAINT movimentacao_credito_origem_ck CHECK (origem IN ('COMPRA', 'CAPTURA')),
	CONSTRAINT movimentacao_credito_quantidade_ck CHECK (quantidade > 0),
	CONSTRAINT movimentacao_credito_saldo_ck CHECK (saldo_apos >= 0)
);

CREATE INDEX movimentacao_credito_usuario_idx ON movimentacao_credito (usuario_id, criado_em);

CREATE FUNCTION impedir_alteracao_movimentacao() RETURNS trigger AS $$
BEGIN
	RAISE EXCEPTION 'As movimentações de crédito aceitam apenas inserções' USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER movimentacao_credito_somente_insercao_trg
	BEFORE UPDATE OR DELETE ON movimentacao_credito
	FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_movimentacao();

CREATE TRIGGER movimentacao_credito_sem_truncate_trg
	BEFORE TRUNCATE ON movimentacao_credito
	FOR EACH STATEMENT EXECUTE FUNCTION impedir_alteracao_movimentacao();

-- Pedido de compra. Guarda uma cópia do pacote e os dados de faturamento do Apêndice A, que são dados pessoais:
-- nunca vão para logs nem para a auditoria (a política de retenção está pendente).
CREATE TABLE pedido_credito (
	id UUID NOT NULL,
	usuario_id UUID NOT NULL,
	chave_idempotencia VARCHAR(100) NOT NULL,
	pacote_id UUID NOT NULL,
	pacote_codigo VARCHAR(20) NOT NULL,
	pacote_nome VARCHAR(60) NOT NULL,
	creditos INTEGER NOT NULL,
	valor_por_credito NUMERIC(10, 2) NOT NULL,
	valor_total NUMERIC(12, 2) NOT NULL,
	metodo VARCHAR(20) NOT NULL,
	status VARCHAR(20) NOT NULL,
	pagamento_externo_id VARCHAR(100),
	checkout_url VARCHAR(2048),
	pix_copia_cola TEXT,
	pix_qr_code TEXT,
	pix_expira_em TIMESTAMP WITH TIME ZONE,
	fat_nome VARCHAR(150) NOT NULL,
	fat_email VARCHAR(254) NOT NULL,
	fat_telefone VARCHAR(11) NOT NULL,
	fat_cpf VARCHAR(11) NOT NULL,
	fat_cep VARCHAR(8) NOT NULL,
	fat_logradouro VARCHAR(150) NOT NULL,
	fat_numero VARCHAR(20) NOT NULL,
	fat_complemento VARCHAR(100),
	fat_bairro VARCHAR(100) NOT NULL,
	fat_cidade VARCHAR(100) NOT NULL,
	fat_uf VARCHAR(2) NOT NULL,
	criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
	decidido_em TIMESTAMP WITH TIME ZONE,
	CONSTRAINT pedido_credito_pk PRIMARY KEY (id),
	CONSTRAINT pedido_credito_pacote_fk FOREIGN KEY (pacote_id) REFERENCES pacote_credito (id),
	CONSTRAINT pedido_credito_idempotencia_uk UNIQUE (usuario_id, chave_idempotencia),
	CONSTRAINT pedido_credito_pagamento_externo_uk UNIQUE (pagamento_externo_id),
	CONSTRAINT pedido_credito_creditos_ck CHECK (creditos > 0),
	CONSTRAINT pedido_credito_valor_ck CHECK (valor_por_credito > 0),
	CONSTRAINT pedido_credito_total_ck CHECK (valor_total = creditos * valor_por_credito),
	CONSTRAINT pedido_credito_metodo_ck CHECK (metodo IN ('PIX', 'MERCADO_PAGO')),
	CONSTRAINT pedido_credito_status_ck CHECK (status IN ('PENDENTE', 'APROVADO', 'RECUSADO', 'EXPIRADO')),
	CONSTRAINT pedido_credito_decisao_ck CHECK ((status = 'PENDENTE') = (decidido_em IS NULL)),
	CONSTRAINT pedido_credito_telefone_ck CHECK (fat_telefone ~ '^[0-9]{10,11}$'),
	CONSTRAINT pedido_credito_cpf_ck CHECK (fat_cpf ~ '^[0-9]{11}$'),
	CONSTRAINT pedido_credito_cep_ck CHECK (fat_cep ~ '^[0-9]{8}$'),
	CONSTRAINT pedido_credito_email_ck CHECK (fat_email = LOWER(fat_email)),
	CONSTRAINT pedido_credito_uf_ck CHECK (fat_uf IN ('AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT',
		'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO'))
);

CREATE INDEX pedido_credito_usuario_idx ON pedido_credito (usuario_id, criado_em DESC);
