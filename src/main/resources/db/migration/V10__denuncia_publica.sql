-- V10: denúncias feitas sem conta, pelo canal público. A descrição, a referência e o e-mail de
-- contato ficam só aqui: o aviso por e-mail leva o motivo e o link do painel. A moderação (Etapa 8)
-- lê as pendentes e grava a resolução; a política de retenção apaga as resolvidas 183 dias depois
-- de criadas.
create table denuncia_publica (
    id            uuid        primary key,
    motivo        text        not null check (motivo in
        ('PARECE_MENOR', 'ABUSO_INFANTIL', 'IMAGEM_INTIMA', 'ASSEDIO', 'GOLPE', 'OUTRO')),
    descricao     text        not null check (char_length(descricao) between 10 and 2000),
    referencia    text        check (char_length(referencia) <= 200),
    email_contato text        check (char_length(email_contato) <= 254),
    criada_em     timestamptz not null,
    situacao      text        not null default 'PENDENTE'
        check (situacao in ('PENDENTE', 'RESOLVIDA'))
);

-- Para a moderação listar as pendentes por prazo sem varrer as resolvidas.
create index denuncia_publica_pendente on denuncia_publica (criada_em) where situacao = 'PENDENTE';
