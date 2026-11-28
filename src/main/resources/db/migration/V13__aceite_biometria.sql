-- V13: o consentimento biométrico da verificação de idade (LGPD art. 11, I) vira aceite versionado,
-- como termos e privacidade: versão e SHA-256 de legal/biometria.md, gravados na primeira sessão de
-- cada versão. A retenção segue a conta (on delete cascade da V7).
alter table aceite_documento drop constraint aceite_documento_documento_check;
alter table aceite_documento
    add constraint aceite_documento_documento_check
        check (documento in ('TERMOS', 'PRIVACIDADE', 'BIOMETRIA'));
