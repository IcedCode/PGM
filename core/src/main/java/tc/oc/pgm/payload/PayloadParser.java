package tc.oc.pgm.payload;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.util.Vector;
import org.jdom2.Element;
import tc.oc.pgm.api.filter.Filter;
import tc.oc.pgm.api.map.factory.MapFactory;
import tc.oc.pgm.filters.FilterParser;
import tc.oc.pgm.goals.ControllableGoalDefinition;
import tc.oc.pgm.teams.TeamFactory;
import tc.oc.pgm.teams.TeamModule;
import tc.oc.pgm.util.xml.InvalidXMLException;
import tc.oc.pgm.util.xml.XMLUtils;

public class PayloadParser {

  private static Element payloadElement;

  public static PayloadDefinition parsePayloadDefinition(MapFactory factory, Element el)
      throws InvalidXMLException {
    payloadElement = el;

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

    float radius = parseFloat("radius", 3.5f);
    float height = parseFloat("height", 5f);

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

    float primaryOwnerSpeed = parseFloat("primary-owner-speed", 1f);
    float primaryOwnerSpeedMultiplier = parseFloat("primary-owner-speed-multiplier", 1f);
    float secondaryOwnerSpeed = parseFloat("secondary-owner-speed", 1f);
    float secondaryOwnerSpeedMultiplier = parseFloat("secondary-owner-speed-multiplier", 1f);
    float neutralSpeed = parseFloat("neutral-speed", 0f);

    boolean permanent = XMLUtils.parseBoolean(el.getAttribute("permanent"), false);

    float points = parseFloat("points", 1f);

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

  // This method was made from anger over boilerplate
  private static float parseFloat(String name, Float def) throws InvalidXMLException {
    return XMLUtils.parseNumber(payloadElement.getAttribute(name), Float.class, def);
  }
}
