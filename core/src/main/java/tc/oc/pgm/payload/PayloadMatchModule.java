package tc.oc.pgm.payload;

import java.util.List;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.module.exception.ModuleLoadException;

public class PayloadMatchModule implements MatchModule {

  private final List<Payload> payloads;

  PayloadMatchModule(Match match, List<Payload> payloads) {
    this.payloads = payloads;

    match.addTickable(new PayloadTickTask(payloads), MatchScope.RUNNING);
  }

  @Override
  public void load() throws ModuleLoadException {
    for (Payload payload : payloads) {
      payload.registerEvents();
    }
  }

  @Override
  public void unload() {
    for (Payload payload : payloads) {
      payload.unregisterEvents();
    }
  }
}
