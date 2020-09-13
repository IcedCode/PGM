package tc.oc.pgm.filters;

import tc.oc.pgm.api.feature.FeatureReference;
import tc.oc.pgm.filters.query.MatchQuery;
import tc.oc.pgm.payload.Payload;
import tc.oc.pgm.payload.PayloadDefinition;

/** Checks if a given checkpoint has been passed by the given payload */
public class PayloadCheckpointFilter extends TypedFilter<MatchQuery> {

  private final FeatureReference<? extends PayloadDefinition> payload;

  // A string in the form of "[ps]\d+"
  private final String checkpointID;

  public PayloadCheckpointFilter(
      FeatureReference<? extends PayloadDefinition> payload, String checkpointID) {
    this.payload = payload;
    this.checkpointID = checkpointID;
  }

  @Override
  public Class<? extends MatchQuery> getQueryType() {
    return MatchQuery.class;
  }

  @Override
  protected QueryResponse queryTyped(MatchQuery query) {
    Payload payload1 = (Payload) payload.get().getGoal(query.getMatch());
    if (payload1 == null) return QueryResponse.ABSTAIN;

    // If no checkpoints has been reached
    if (payload1.getLastReachedCheckpoint() == null) return QueryResponse.DENY;

    int lastReachedCheckpointKey = payload1.getLastReachedCheckpoint().getMapIndex();

    // At this point we know that the String is a valid checkpoint(regexed in the filter parser)
    if ((checkpointID.startsWith("s") && lastReachedCheckpointKey < 0)
        || checkpointID.startsWith("p") && lastReachedCheckpointKey >= 0) {

      return QueryResponse.fromBoolean(
          Math.abs(Integer.parseInt(checkpointID.substring(1)))
              <= Math.abs(lastReachedCheckpointKey));
    } else return QueryResponse.DENY;
  }
}
