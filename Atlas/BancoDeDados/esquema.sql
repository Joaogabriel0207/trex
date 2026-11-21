-- Executar uma vez, em um banco vazio, com o proprietário usado pelo Backend.
BEGIN;

CREATE TABLE funcionarios (
    codigo varchar(32) PRIMARY KEY CHECK (codigo ~ '^[A-Za-z0-9_-]{1,32}$'),
    nome varchar(120) NOT NULL CHECK (btrim(nome) <> ''),
    cargo varchar(24) NOT NULL CHECK (cargo IN
        ('GERENTE', 'GARCOM', 'CAIXA', 'MAITRE', 'SOMMELIER', 'PADEIRO',
         'BARMAN', 'CUMIM', 'COZINHEIRO', 'AUXILIAR_LIMPEZA')),
    ativo boolean NOT NULL,
    senha_hash text NOT NULL
);

CREATE TABLE mesas (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero integer NOT NULL UNIQUE CHECK (numero > 0)
);

CREATE TABLE categorias (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome varchar(120) NOT NULL UNIQUE CHECK (btrim(nome) <> '')
);

CREATE TABLE produtos (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome varchar(120) NOT NULL CHECK (btrim(nome) <> ''),
    categoria_id integer NOT NULL REFERENCES categorias,
    preco_centavos bigint NOT NULL CHECK (preco_centavos BETWEEN 1 AND 100000000),
    setor varchar(10) NOT NULL CHECK (setor IN ('BAR', 'COZINHA')),
    disponivel boolean NOT NULL
);

CREATE TABLE comandas (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mesa_id integer NOT NULL REFERENCES mesas,
    funcionario_codigo varchar(32) NOT NULL REFERENCES funcionarios,
    estado varchar(20) NOT NULL CHECK (estado IN ('ABERTA','CONTA_SOLICITADA','ENCERRADA')),
    abertura timestamptz NOT NULL DEFAULT current_timestamp,
    encerramento timestamptz,
    CHECK ((estado = 'ENCERRADA') = (encerramento IS NOT NULL))
);
CREATE UNIQUE INDEX uma_comanda_por_mesa ON comandas (mesa_id) WHERE estado <> 'ENCERRADA';

CREATE TABLE pedidos (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comanda_id integer NOT NULL REFERENCES comandas,
    funcionario_codigo varchar(32) NOT NULL REFERENCES funcionarios,
    chave uuid NOT NULL,
    estado varchar(20) NOT NULL CHECK (estado IN ('REGISTRADO','PRONTO','ENTREGUE')),
    criacao timestamptz NOT NULL DEFAULT current_timestamp,
    UNIQUE (funcionario_codigo, chave)
);
CREATE INDEX pedidos_por_comanda ON pedidos (comanda_id);

-- Nome, setor e preço registram o produto servido, mesmo após uma edição do cardápio.
CREATE TABLE itens_pedido (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id integer NOT NULL REFERENCES pedidos,
    produto_id integer NOT NULL REFERENCES produtos,
    nome varchar(120) NOT NULL,
    setor varchar(10) NOT NULL CHECK (setor IN ('BAR', 'COZINHA')),
    preco_centavos bigint NOT NULL CHECK (preco_centavos BETWEEN 1 AND 100000000),
    quantidade integer NOT NULL CHECK (quantidade BETWEEN 1 AND 999),
    observacao varchar(240) NOT NULL DEFAULT ''
);
CREATE INDEX itens_por_pedido ON itens_pedido (pedido_id);

CREATE TABLE pagamentos (
    comanda_id integer PRIMARY KEY REFERENCES comandas,
    funcionario_codigo varchar(32) NOT NULL REFERENCES funcionarios,
    valor_centavos bigint NOT NULL CHECK (valor_centavos >= 0),
    forma varchar(20) NOT NULL CHECK (forma IN ('DINHEIRO', 'CARTAO', 'PIX')),
    registro timestamptz NOT NULL DEFAULT current_timestamp
);

CREATE TABLE tarefas_impressao (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id integer NOT NULL REFERENCES pedidos,
    setor varchar(10) NOT NULL CHECK (setor IN ('BAR', 'COZINHA')),
    estado varchar(20) NOT NULL CHECK (estado IN
        ('PENDENTE', 'EM_TENTATIVA', 'ENVIADA', 'FALHA', 'INCERTA')),
    tentativa integer NOT NULL DEFAULT 0 CHECK (tentativa >= 0),
    responsavel_codigo varchar(32) REFERENCES funcionarios,
    confirmado_por varchar(32) REFERENCES funcionarios,
    detalhe varchar(500) NOT NULL DEFAULT '',
    UNIQUE (pedido_id, setor)
);

COMMIT;
