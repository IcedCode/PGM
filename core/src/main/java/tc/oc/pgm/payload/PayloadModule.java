package tc.oc.pgm.payload;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.jdom2.Document;
import org.jdom2.Element;
import tc.oc.pgm.api.map.MapModule;
import tc.oc.pgm.api.map.MapTag;
import tc.oc.pgm.api.map.factory.MapFactory;
import tc.oc.pgm.api.map.factory.MapModuleFactory;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.module.exception.ModuleLoadException;
import tc.oc.pgm.goals.GoalMatchModule;
import tc.oc.pgm.teams.TeamModule;
import tc.oc.pgm.util.xml.InvalidXMLException;
import tc.oc.pgm.util.xml.XMLUtils;

public class PayloadModule implements MapModule<PayloadMatchModule> {

  private final List<PayloadDefinition> definitions;

  public PayloadModule(List<PayloadDefinition> definitions) {
    this.definitions = definitions;
  }

  @Override
  public Collection<Class<? extends MatchModule>> getSoftDependencies() {
    return ImmutableList.of(GoalMatchModule.class);
  }

  @Nullable
  @Override
  public PayloadMatchModule createMatchModule(Match match) throws ModuleLoadException {

    final List<Payload> payloads = new LinkedList<>();

    for (PayloadDefinition definition : definitions) {
      Payload payload = new Payload(match, definition);
      match.getFeatureContext().add(payload);
      match.needModule(GoalMatchModule.class).addGoal(payload);
      payloads.add(payload);
    }

    return new PayloadMatchModule(match, payloads);
  }

  @Override
  public Collection<MapTag> getTags() {
    return ImmutableList.of(MapTag.create("payload", "Payload", true, false));
  }

  public static class Factory implements MapModuleFactory<PayloadModule> {

    @Nullable
    @Override
    public PayloadModule parse(MapFactory factory, Logger logger, Document doc)
        throws InvalidXMLException {
      List<PayloadDefinition> definitions = new ArrayList<>();

      for (Element payloadEl :
          XMLUtils.flattenElements(doc.getRootElement(), "payloads", "payload")) {
        definitions.add(PayloadParser.parsePayloadDefinition(factory, payloadEl));
      }
      if (definitions.isEmpty()) return null;
      return new PayloadModule(definitions);
    }

    @Nullable
    @Override
    public Collection<Class<? extends MapModule>> getHardDependencies() {
      return ImmutableList.of(TeamModule.class);
    }
  }
}
