-- Localização da conta (Etapa 3, D3): só a célula geohash de 5 caracteres (cerca de 5 km), nunca a
-- coordenada. Referencia conta, não perfil, para não depender do perfil. A conta excluída fica como
-- lápide, então o cascade é só rede de segurança: o LocalizacaoAoExcluirConta apaga a linha, e a
-- RetencaoDeLocalizacoes apaga a célula parada há 90 dias (daí o índice em atualizada_em).
create table localizacao (
    conta_id uuid primary key references conta(id) on delete cascade,
    celula text not null check (celula ~ '^[0-9b-hjkmnp-z]{5}$'),
    consentida_em timestamptz not null,
    atualizada_em timestamptz not null,
    check (atualizada_em >= consentida_em)
);

create index localizacao_atualizada_em_idx on localizacao (atualizada_em);
