package bipotheking.net.duora.room;

import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Varredura periódica única (sem thread por sala). */
@Component
class Varredor {

  private static final long INTERVALO_SEGUNDOS = 60;

  private final Salas salas;

  Varredor(Salas salas) {
    this.salas = salas;
  }

  @Scheduled(fixedDelay = INTERVALO_SEGUNDOS, timeUnit = TimeUnit.SECONDS)
  void varrer() {
    salas.varrer();
  }
}
