BEGIN;

ALTER TABLE produtos ADD COLUMN descricao varchar(500) NOT NULL DEFAULT '',
                     ADD COLUMN imagem varchar(2048) NOT NULL DEFAULT '';
ALTER TABLE funcionarios ADD COLUMN expediente varchar(120) NOT NULL DEFAULT '';

ALTER TABLE itens_pedido
    ADD COLUMN produto_id_historico integer,
    ADD COLUMN categoria varchar(120),
    ALTER COLUMN produto_id DROP NOT NULL,
    DROP CONSTRAINT itens_pedido_produto_id_fkey,
    ADD CONSTRAINT itens_pedido_produto_id_fkey FOREIGN KEY (produto_id) REFERENCES produtos ON DELETE SET NULL;
UPDATE itens_pedido i SET categoria = c.nome
FROM produtos p JOIN categorias c ON c.id = p.categoria_id WHERE i.produto_id = p.id;
CREATE INDEX itens_pedido_por_produto ON itens_pedido (produto_id);

CREATE FUNCTION preservar_produto_excluido() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE itens_pedido SET produto_id_historico = OLD.id,
        categoria = coalesce(categoria, (SELECT nome FROM categorias WHERE id = OLD.categoria_id))
    WHERE produto_id = OLD.id;
    RETURN OLD;
END;
$$;
CREATE TRIGGER preservar_produto_excluido BEFORE DELETE ON produtos
FOR EACH ROW EXECUTE FUNCTION preservar_produto_excluido();

ALTER TABLE comandas
    ADD COLUMN funcionario_codigo_historico varchar(32),
    ADD COLUMN funcionario_nome_historico varchar(120),
    ALTER COLUMN funcionario_codigo DROP NOT NULL,
    DROP CONSTRAINT comandas_funcionario_codigo_fkey,
    ADD CONSTRAINT comandas_funcionario_codigo_fkey FOREIGN KEY (funcionario_codigo) REFERENCES funcionarios ON DELETE SET NULL;
ALTER TABLE pedidos
    ADD COLUMN funcionario_codigo_historico varchar(32),
    ADD COLUMN funcionario_nome_historico varchar(120),
    ALTER COLUMN funcionario_codigo DROP NOT NULL,
    DROP CONSTRAINT pedidos_funcionario_codigo_fkey,
    ADD CONSTRAINT pedidos_funcionario_codigo_fkey FOREIGN KEY (funcionario_codigo) REFERENCES funcionarios ON DELETE SET NULL;
ALTER TABLE pagamentos
    ADD COLUMN funcionario_codigo_historico varchar(32),
    ADD COLUMN funcionario_nome_historico varchar(120),
    ALTER COLUMN funcionario_codigo DROP NOT NULL,
    DROP CONSTRAINT pagamentos_funcionario_codigo_fkey,
    ADD CONSTRAINT pagamentos_funcionario_codigo_fkey FOREIGN KEY (funcionario_codigo) REFERENCES funcionarios ON DELETE SET NULL;
ALTER TABLE tarefas_impressao
    ADD COLUMN responsavel_codigo_historico varchar(32),
    ADD COLUMN responsavel_nome_historico varchar(120),
    ADD COLUMN confirmado_codigo_historico varchar(32),
    ADD COLUMN confirmado_nome_historico varchar(120),
    DROP CONSTRAINT tarefas_impressao_responsavel_codigo_fkey,
    ADD CONSTRAINT tarefas_impressao_responsavel_codigo_fkey FOREIGN KEY (responsavel_codigo) REFERENCES funcionarios ON DELETE SET NULL,
    DROP CONSTRAINT tarefas_impressao_confirmado_por_fkey,
    ADD CONSTRAINT tarefas_impressao_confirmado_por_fkey FOREIGN KEY (confirmado_por) REFERENCES funcionarios ON DELETE SET NULL;

CREATE INDEX comandas_por_funcionario ON comandas (funcionario_codigo);
CREATE INDEX pagamentos_por_funcionario ON pagamentos (funcionario_codigo);
CREATE INDEX tarefas_por_responsavel ON tarefas_impressao (responsavel_codigo);
CREATE INDEX tarefas_por_confirmador ON tarefas_impressao (confirmado_por);

CREATE FUNCTION preservar_funcionario_excluido() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE comandas SET funcionario_codigo_historico = OLD.codigo, funcionario_nome_historico = OLD.nome
    WHERE funcionario_codigo = OLD.codigo;
    UPDATE pedidos SET funcionario_codigo_historico = OLD.codigo, funcionario_nome_historico = OLD.nome
    WHERE funcionario_codigo = OLD.codigo;
    UPDATE pagamentos SET funcionario_codigo_historico = OLD.codigo, funcionario_nome_historico = OLD.nome
    WHERE funcionario_codigo = OLD.codigo;
    UPDATE tarefas_impressao SET responsavel_codigo_historico = OLD.codigo, responsavel_nome_historico = OLD.nome
    WHERE responsavel_codigo = OLD.codigo;
    UPDATE tarefas_impressao SET confirmado_codigo_historico = OLD.codigo, confirmado_nome_historico = OLD.nome
    WHERE confirmado_por = OLD.codigo;
    RETURN OLD;
END;
$$;
CREATE TRIGGER preservar_funcionario_excluido BEFORE DELETE ON funcionarios
FOR EACH ROW EXECUTE FUNCTION preservar_funcionario_excluido();

COMMIT;
