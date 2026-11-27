-- V3: outbox transacional. A mensagem entra na mesma transação do fato (sem dual write) e o
-- despachante a entrega depois, com retry e backoff. A carga só existe enquanto a mensagem está
-- pendente (D-1): ao enviar ou falhar de vez, ela é apagada, e o check garante isso.
create table outbox (
    id                   uuid        primary key,
    tipo                 text        not null,
    titular              text        not null,
    carga                jsonb,
    correlation_id       text,
    criada_em            timestamptz not null,
    proxima_tentativa_em timestamptz not null,
    reservada_ate        timestamptz,
    tentativas           int         not null default 0 check (tentativas >= 0),
    enviada_em           timestamptz,
    falhou_em            timestamptz,
    ultimo_erro          text,
    check ((carga is not null) = (enviada_em is null and falhou_em is null))
);

-- A fila do despachante: só as pendentes, pela próxima tentativa.
create index outbox_pendentes on outbox (proxima_tentativa_em) where carga is not null;

-- Para esquecer o titular (exclusão de conta) sem varrer a tabela.
create index outbox_pendentes_do_titular on outbox (titular) where carga is not null;
