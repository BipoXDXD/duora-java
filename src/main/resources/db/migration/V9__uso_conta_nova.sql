-- V9: uso diário de conta nova. Nos primeiros 7 dias depois de ativada_em, a conta tem limite por
-- dia de São Paulo para mensagens, imagens e convites. O consumo é um upsert condicional na
-- transação do efeito; a política de retenção apaga o dia 8 dias depois do fim dele.
create table uso_conta_nova (
    conta_id uuid not null references conta (id) on delete cascade,
    dia      date not null,
    tipo     text not null check (tipo in ('MENSAGEM', 'IMAGEM', 'CONVITE')),
    usados   int  not null check (usados >= 0),
    primary key (conta_id, dia, tipo)
);

-- Para a retenção achar os dias vencidos sem varrer a tabela.
create index uso_conta_nova_dia on uso_conta_nova (dia);
