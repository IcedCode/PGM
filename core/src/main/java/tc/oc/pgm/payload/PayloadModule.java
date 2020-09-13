package tc.oc.pgm.payload;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.bukkit.util.Vector;
import org.jdom2.Document;
import org.jdom2.Element;
import tc.oc.pgm.api.filter.Filter;
import tc.oc.pgm.api.map.MapModule;
import tc.oc.pgm.api.map.MapTag;
import tc.oc.pgm.api.map.factory.MapFactory;
import tc.oc.pgm.api.map.factory.MapModuleFactory;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.module.exception.ModuleLoadException;
import tc.oc.pgm.filters.FilterParser;
import tc.oc.pgm.goals.ControllableGoalDefinition;
import tc.oc.pgm.goals.GoalMatchModule;
import tc.oc.pgm.teams.TeamFactory;
import tc.oc.pgm.teams.TeamMatchModule;
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
    return ImmutableList.of(GoalMatchModule.class, TeamMatchModule.class);
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
        PayloadDefinition definition = parsePayloadDefinition(factory, payloadEl);
        factory.getFeatures().addFeature(payloadEl, definition);
        definitions.add(definition);
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

  public static PayloadDefinition parsePayloadDefinition(MapFactory factory, Element el)
      throws InvalidXMLException {

    FilterParser filterParser = factory.getFilters();

    String id = el.getAttributeValue("id");
    String name = el.getAttributeValue("name", "Payload");
    boolean visible = XMLUtils.parseBoolean(el.getAttribute("visible"), true);

    Vector startingLocation = XMLUtils.parseVector(el.getAttribute("starting-location"));

    Vector middleLocation = XMLUtils.parseVector(el.getAttribute("middle-location"));

    boolean shouldSecondaryTeamPushButNoGoal =
        XMLUtils.parseBoolean(el.getAttribute("secondary-push-nogoal"), false);

    Filter playerPushFilter = filterParser.parseFilterProperty(el, "push-filter");
    Filter playerDominateFilter = filterParser.parseFilterProperty(el, "player-filter");

    TeamModule teams = factory.getModule(TeamModule.class);

    // TODO: Throw error if secondary but NOT primary team is specified, throw error if they are the
    // same
    TeamFactory primaryOwner =
        teams == null ? null : teams.parseTeam(el.getAttribute("primary-owner"), factory);
    TeamFactory secondaryOwner =
        teams == null ? null : teams.parseTeam(el.getAttribute("secondary-owner"), factory);

    ControllableGoalDefinition.CaptureCondition captureCondition =
        ControllableGoalDefinition.parseCaptureCondition(el);

    float radius = parseFloat("radius", 3.5f, el);
    float height = parseFloat("height", 5f, el);

    // MaterialMatcher checkpointMaterials =
    // XMLUtils.parseMaterialMatcher(el.getChild("checkpoint-material-matcher"));

    List<Integer> permanentHeadCheckpoints = new ArrayList<>();
    List<Integer> permanentTailCheckpoints = new ArrayList<>();

    for (Element element : el.getChild("permanent-checkpoints").getChildren()) {
      String string = element.getName();
      if (string.startsWith("p"))
        permanentHeadCheckpoints.add(Integer.parseInt(string.substring(1)));
      if (string.startsWith("s"))
        permanentTailCheckpoints.add(Integer.parseInt(string.substring(1)));
    }

    float primaryOwnerSpeed = parseFloat("primary-owner-speed", 1f, el);
    float primaryOwnerSpeedMultiplier = parseFloat("primary-owner-speed-multiplier", 1f, el);
    float secondaryOwnerSpeed = parseFloat("secondary-owner-speed", 1f, el);
    float secondaryOwnerSpeedMultiplier = parseFloat("secondary-owner-speed-multiplier", 1f, el);
    float neutralSpeed = parseFloat("neutral-speed", 0.2f, el);

    boolean permanent = XMLUtils.parseBoolean(el.getAttribute("permanent"), false);

    float points = parseFloat("points", 0f, el);

    boolean showProgress = XMLUtils.parseBoolean(el.getAttribute("show-progress"), true);

    boolean required = XMLUtils.parseBoolean(el.getAttribute("required"), true);

    return new PayloadDefinition(
        id,
        name,
        required,
        visible,
        startingLocation,
        middleLocation,
        playerPushFilter,
        playerDominateFilter,
        primaryOwner,
        secondaryOwner,
        captureCondition,
        radius,
        height,
        shouldSecondaryTeamPushButNoGoal,
        null,
        permanentHeadCheckpoints,
        permanentTailCheckpoints,
        primaryOwnerSpeed,
        primaryOwnerSpeedMultiplier,
        secondaryOwnerSpeed,
        secondaryOwnerSpeedMultiplier,
        neutralSpeed,
        permanent,
        points,
        showProgress);
  }

  // Why did i make this method again?...
  private static float parseFloat(String name, Float def, Element el) throws InvalidXMLException {
    return XMLUtils.parseNumber(el.getAttribute(name), Float.class, def);
  }
}
