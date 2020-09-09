package tc.oc.pgm.payload;

import java.util.List;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;

public class PayloadMatchModule implements MatchModule {

  PayloadMatchModule(Match match, List<Payload> payloads) {
    match.addTickable(new PayloadTickTask(payloads), MatchScope.RUNNING);
  }
}
