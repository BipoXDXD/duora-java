-- V7: aceite versionado dos documentos legais no cadastro. Cada linha aponta para o texto exato
-- aceito (versão do front matter e SHA-256 do texto servido); a PK impede aceite duplicado e,
-- por começar em conta_id, serve para apagar os aceites da conta.
create table aceite_documento (
    conta_id      uuid        not null references conta (id) on delete cascade,
    documento     text        not null check (documento in ('TERMOS', 'PRIVACIDADE')),
    versao        text        not null,
    resumo_sha256 text        not null check (resumo_sha256 ~ '^[0-9a-f]{64}$'),
    aceito_em     timestamptz not null,
    primary key (conta_id, documento, versao)
);
