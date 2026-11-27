package bipotheking.net.duora.arquitetura.exemplos.moduloc.web;

import software.amazon.awssdk.regions.Region;

/** Viola a fronteira do fornecedor: o SDK da AWS só aparece em {@code infra/}. */
class UsaAws {

  private final Region regiao;

  UsaAws(Region regiao) {
    this.regiao = regiao;
  }

  String regiao() {
    return regiao.id();
  }
}
