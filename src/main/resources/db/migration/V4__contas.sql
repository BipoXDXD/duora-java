-- V4: contas. O banco guarda as invariantes: e-mail único e normalizado, só a conta excluída sem
-- e-mail (o unique deixa várias linhas com e-mail nulo) e data de nascimento obrigatória a partir
-- do cadastro concluído.
create table conta (
    id              uuid        primary key,
    email           text        unique,
    estado          text        not null check (estado in
                        ('PENDENTE', 'AGUARDANDO_VERIFICACAO', 'ATIVA', 'SUSPENSA', 'BANIDA', 'EXCLUIDA')),
    data_nascimento date,
    criada_em       timestamptz not null,
    ativada_em      timestamptz,
    excluida_em     timestamptz,
    check (email = lower(email)),
    check ((estado = 'EXCLUIDA') = (email is null)),
    check (estado not in ('AGUARDANDO_VERIFICACAO', 'ATIVA', 'SUSPENSA', 'BANIDA')
           or data_nascimento is not null),
    -- Suspensa e banida podem não ter ativada_em: a moderação bane até conta pendente.
    check (estado <> 'ATIVA' or ativada_em is not null),
    check ((estado = 'EXCLUIDA') = (excluida_em is not null))
);
