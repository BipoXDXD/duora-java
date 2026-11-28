-- Ajustes de busca e dados de namoro (Etapa 3, D2).
--
-- preferencias: faixa etária e raio. Um raio diferente de SEM_LIMITE só entra com a localização
-- ativa (o INSERT/UPDATE confere com exists ... for key share); a revogação e a retenção da
-- localização o voltam para SEM_LIMITE. Sai junto com o perfil (cascade), que o
-- PerfilAoExcluirConta apaga de verdade.
create table preferencias (
    conta_id uuid primary key references perfil(conta_id) on delete cascade,
    idade_minima smallint not null check (idade_minima between 18 and 99),
    idade_maxima smallint not null check (idade_maxima between 18 and 99),
    raio text not null check (raio in ('ATE_5_KM','ATE_20_KM','ATE_50_KM','SEM_LIMITE')),
    raio_obrigatorio boolean not null,
    versao bigint not null check (versao >= 1),
    atualizado_em timestamptz not null,
    check (idade_minima <= idade_maxima),
    check (raio <> 'SEM_LIMITE' or not raio_obrigatorio)
);

-- Alvo da chave composta do namoro: a linha de namoro só existe com perfil.quer_namoro verdadeiro.
alter table perfil add constraint perfil_conta_namoro_uk unique (conta_id, quer_namoro);

-- namoro: gênero e interesses, dados sensíveis (LGPD, art. 11), só com consentimento. A chave
-- composta com quer_namoro sempre verdadeiro prende a linha à intenção: desligar o namoro com a
-- linha presente falha no próprio UPDATE do perfil (NO ACTION), e gravar sem a intenção falha no
-- INSERT. Sai junto com o perfil (cascade).
create table namoro (
    conta_id uuid primary key,
    quer_namoro boolean not null default true check (quer_namoro),
    genero text not null check (genero in ('MULHER','HOMEM','PESSOA_NAO_BINARIA','OUTRO')),
    descricao_genero text check (char_length(descricao_genero) between 1 and 40),
    interesses text[] not null check (
        cardinality(interesses) >= 1
        and interesses <@ array['MULHER','HOMEM','PESSOA_NAO_BINARIA','OUTRO']::text[]),
    consentido_em timestamptz not null,
    check ((genero = 'OUTRO') = (descricao_genero is not null)),
    constraint namoro_exige_intencao_fk foreign key (conta_id, quer_namoro)
        references perfil (conta_id, quer_namoro) on delete cascade
);
