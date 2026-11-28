-- Orientação e pausa da conta (Etapa 3, D4).
--
-- orientacao: dado sensível (LGPD, art. 11), só com consentimento próprio, registrado em
-- consentida_em. OUTRA vem com descrição de 1 a 40 caracteres, e só ela. mostrar decide se a
-- orientação entra no perfil que as conexões veem. Sai junto com o perfil (cascade), que o
-- PerfilAoExcluirConta apaga de verdade.
create table orientacao (
    conta_id uuid primary key references perfil(conta_id) on delete cascade,
    orientacao text not null check (orientacao in ('HETEROSSEXUAL','GAY','LESBICA','BISSEXUAL',
        'PANSEXUAL','ASSEXUAL','DEMISSEXUAL','QUEER','QUESTIONANDO','OUTRA')),
    descricao text check (char_length(descricao) between 1 and 40),
    mostrar boolean not null,
    consentida_em timestamptz not null,
    check ((orientacao = 'OUTRA') = (descricao is not null))
);

-- pausada_em: quando a pessoa saiu da fila; nulo = aparece. Não mexe na versão do perfil.
alter table perfil add column pausada_em timestamptz;
