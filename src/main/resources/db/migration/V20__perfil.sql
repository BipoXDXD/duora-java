-- Perfil básico da conta (Etapa 3, D1). A versão é o controle otimista das edições. A conta
-- excluída fica como lápide, então o cascade é só rede de segurança: o PerfilAoExcluirConta apaga
-- a linha na exclusão.
create table perfil (
    conta_id uuid primary key references conta(id) on delete cascade,
    apelido text not null check (char_length(apelido) between 1 and 20),
    pronomes text check (pronomes in ('ELA_DELA', 'ELE_DELE', 'ELU_DELU', 'OUTROS')),
    descricao_pronomes text check (char_length(descricao_pronomes) between 1 and 20),
    quer_amizade boolean not null,
    quer_namoro boolean not null,
    bio text not null default '' check (char_length(bio) <= 280),
    versao bigint not null check (versao >= 1),
    criado_em timestamptz not null,
    atualizado_em timestamptz not null,
    check (quer_amizade or quer_namoro),
    check ((pronomes is not distinct from 'OUTROS') = (descricao_pronomes is not null))
);
