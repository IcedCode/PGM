package tc.oc.pgm.payload;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Rails;
import org.bukkit.util.Vector;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.party.Party;
import tc.oc.pgm.goals.ControllableGoal;
import tc.oc.pgm.goals.IncrementalGoal;
import tc.oc.pgm.goals.events.GoalCompleteEvent;
import tc.oc.pgm.goals.events.GoalStatusChangeEvent;
import tc.oc.pgm.regions.CylindricalRegion;
import tc.oc.pgm.teams.Team;
import tc.oc.pgm.teams.TeamMatchModule;
import tc.oc.pgm.util.bukkit.BukkitUtils;
import tc.oc.pgm.util.material.MaterialMatcher;
import tc.oc.pgm.util.material.matcher.CompoundMaterialMatcher;
import tc.oc.pgm.util.material.matcher.SingleMaterialMatcher;

// ---------------------------Terminology-----------------------------
// -- Rail -> A collection of nested Paths(Stored in each other)     I
// --                                                                I
// -- Path -> A point on the rail.                                   I
// --         When the payload moves it moves from path to path.     I
// --         This does not mean that each single possible point     I
// --         the payload can exist on is a Path, but rather that    I
// --         the movement of the payload is always from one Path    I
// --         to another.                                            I
// --                                                                I
// --                                                                I
// -- Head/Tail -> Head is towards a higher index on the rail(->max) I
// --              tail is towards a lower index on the rail(->0)    I
// -------------------------------------------------------------------

public class Payload extends ControllableGoal<PayloadDefinition>
    implements IncrementalGoal<PayloadDefinition>, Listener {

  public static final ChatColor COLOR_NEUTRAL_TEAM = ChatColor.WHITE;

  public static final String SYMBOL_PAYLOAD_NEUTRAL = "\u29be"; // ⦾
  public static final String SYMBOL_PAYLOAD_PUSHING_PRIMARY = "\u2b46"; // ⭆
  public static final String SYMBOL_PAYLOAD_PUSHING_SECONDARY = "\u2b45"; // ⭅

  // The current location of the payload
  private Location payloadLocation;

  // The team that currently owns the payload
  // Teams that can not push the payload CAN be the owner
  // Is null if no players nearby
  @Nullable private Competitor currentOwner = null;

  // The amount of Paths in this payloads rail
  private int railSize = 0;

  private Minecart payloadEntity;
  private ArmorStand labelEntity;

  public Path headPath;
  public Path tailPath;

  public Path currentPath;

  // The path the payload goes towards in the neutral state, the path the payload starts on;
  private Path
      middlePath; // TODO Make this able to be user-defined(Vector) but default to the middle

  private Path furthestHeadPath;
  private Path furthestTailPath;

  // Switches to true when the payload reaches a end
  private boolean completed = false;

  private final Map<Integer, PayloadCheckpoint> checkpointMap = new HashMap<>();

  private int lastReachedCheckpointKey = Integer.MAX_VALUE; //Starts with

  public Payload(Match match, PayloadDefinition definition) {
    super(definition, match);

    createPayload();
  }

  public Vector getStartingLocation() {
    return definition.getStartingLocation();
  }

  // Incrementable

  @Override
  public double getCompletion() {
    return 329;
  }

  @Override
  public String renderCompletion() {

    final StringBuilder render = new StringBuilder();

    render.append("o");

    // This will render where the payload is between the two closest locations important locations
    // (checkpoints, middle, ends)

    if ((currentPath.index() > checkpointMap.get(-1).index()
            && currentPath.index() < checkpointMap.get(0).index())) {
      // Payload is currently between the first checkpoints in each direction

      //Render will look like "o---X---o" (any symbol can be switched with the payload symbol)


      render.append(buildLines(checkpointMap.get(-1).index(), middlePath.index(), 3));
      render.append(currentPath.index() == middlePath.index() ? renderPayloadSymbol() : "X");
      render.append(buildLines(middlePath.index(), checkpointMap.get(0).index(), 3));
    } else {
      // The payload is past the first checkpoint in some direction

      //Render will look like "o------o"

        if(currentPath.isCheckpoint()){
          render.deleteCharAt(0);
          render.append(renderPayloadSymbol());
        }

        PayloadCheckpoint other;

        /*if(currentPath.index() <= lastReachedCheckpoint.index()){
          other = checkpointMap.get(lastReachedCheckpointKey + 1);
        }
        else {
          other = checkpointMap.get(lastReachedCheckpointKey - 1);
        }*/

        other = checkpointMap.get(lastReachedCheckpointKey + (currentPath.index() <= getLastReachedCheckpoint().index() ? -1 : 1));

      render.append(buildLines(getLastReachedCheckpoint().index(), other.index(), 6));  //TODO cache this instead of updating every tick? lolol
      }

    render.append("o");

    return render.toString();
  }

  private PayloadCheckpoint getLastReachedCheckpoint(){
    return checkpointMap.get(lastReachedCheckpointKey);
  }

  private String buildLines(int a, int b, int lines){
    final int indexPerLine = (int) (Math.ceil(Math.abs(a - b)) / ((double) lines));
    final StringBuilder render = new StringBuilder();
    for (int i = Math.min(a, b); i < Math.max(a, b); i += indexPerLine) {
      if(isCurrentPathBetween(i, i + indexPerLine) || (!currentPath.isCheckpoint() && currentPath.index() != middlePath.index() && i == currentPath.index())) render.append(renderPayloadSymbol());
      else render.append("-");
    }
    return render.toString();
  }

  private String renderPayloadSymbol() {
    return isNeutral()
        ? COLOR_NEUTRAL_TEAM + SYMBOL_PAYLOAD_NEUTRAL + ChatColor.GRAY
        : currentOwner.getColor() + (isUnderPrimaryOwnerControl() ? SYMBOL_PAYLOAD_PUSHING_PRIMARY : SYMBOL_PAYLOAD_PUSHING_SECONDARY) + ChatColor.RESET + ChatColor.GRAY;
  }

  private boolean isCurrentPathBetween(int startIndex, int endIndex) {
    int index = currentPath.index();
    if (startIndex < endIndex) {
      return (startIndex < index && index < endIndex);
    } else {
      return (endIndex < index && index < startIndex);
    }
  }

  public @Nullable String renderPreciseCompletion() {
    return null;
  }

  public boolean getShowProgress() {
    return definition.shouldShowProgress();
  }

  //

  @Override
  public boolean canComplete(
      Competitor
          team) {
    if (team instanceof Team)
      return ((Team) team).isInstance(definition.getPrimaryOwner())
          || (((Team) team).isInstance(definition.getSecondaryOwner())
              && !definition.shouldSecondaryTeamPushButNoGoal()); //If they have no goal they cant complete anything
    return false;
  }

  @Override
  public boolean isCompleted() {
    return completed;
  }

  @Override
  public boolean isCompleted(Competitor team) {
    return isCompleted() && currentOwner != null && currentOwner.equals(team);
  }

  @Override
  public boolean isShared() {
    return true;
  }

  @Override
  public ChatColor renderSidebarStatusColor(@Nullable Competitor competitor, Party viewer) {
    return ChatColor.GRAY;
  }

  public String renderSidebarStatusText(@Nullable Competitor competitor, Party viewer) {
    return renderCompletion();
  }

  public ChatColor renderSidebarLabelColor(@Nullable Competitor competitor, Party viewer) {
    return isNeutral() ? COLOR_NEUTRAL_TEAM : currentOwner.getColor();
  }

  // Controllable

  @Override
  public void dominationCycle(@Nullable Competitor dominatingTeam, int lead, Duration duration) {
    currentOwner = dominatingTeam;
  }

  // Ticking

  /** Called by this {@link Payload}s {@link PayloadTickTask} */
  public void tick(Match match) {
    if (isCompleted()) return; // Freeze if completed

    tickDisplay();
    tickMove();
    updateProx();
    match.callEvent(new GoalStatusChangeEvent(match, this));
  }

  private void tickMove() {

    float speed = getControllingTeamSpeed(); // Fetches the speed for the current owner

    final float points = definition.getPoints();

    if ((isUnderPrimaryOwnerControl() ? !currentPath.hasNext() : !currentPath.hasPrevious()) && !(!isUnderPrimaryOwnerControl() && definition.shouldSecondaryTeamPushButNoGoal())) {
        completed = true;
        match.callEvent(new GoalCompleteEvent(match, this, currentOwner, true));
        match.sendMessage(
            TextComponent.of(
                currentOwner.getNameLegacy() + " Completed the goal 8)", TextColor.GOLD));

       /* final ScoreMatchModule smm = match.needModule(ScoreMatchModule.class);
        if (smm != null) {
          if (currentOwner != null) smm.incrementScore(currentOwner, points);*/
        return;
    }


    speed = Math.abs(speed);
    move(speed / 10.0);

    Location labelLocation = payloadEntity.getLocation().clone();
    labelLocation.setY(labelLocation.getY() - 0.2);
    labelEntity.teleport(labelLocation);

  }

  private void move(double distance) {
    boolean hasNext =
        isUnderPrimaryOwnerControl() ? currentPath.hasNext() : currentPath.hasPrevious();
    if (!hasNext) { // Path is over
      payloadEntity.teleport(currentPath.getLocation()); // Teleport to final location
      return;
    }

    if (currentPath.isCheckpoint()
        && (getLastReachedCheckpoint() == null
            || currentPath.index() != getLastReachedCheckpoint().index())) {
      PayloadCheckpoint newCheckpoint;

      newCheckpoint = calculateCheckpointContext();

      if (newCheckpoint == null) return;

      if (getLastReachedCheckpoint() != newCheckpoint) {

        lastReachedCheckpointKey = newCheckpoint.getMapIndex();

        final Component message =
            TranslatableComponent.of(
                "payloadCheckpoint",
                TextColor.GRAY,
                currentOwner == null
                    ? TextComponent.of("Unknown", TextColor.DARK_AQUA)
                    : currentOwner.getName(), // Put in the owner of the checkpoint
                TextComponent.of(Math.abs(0)));
        match.sendMessage(message);
      }

    }

    if (currentPath.isCheckpoint() && getLastReachedCheckpoint() != null) {
      if (isUnderPrimaryOwnerControl()
          && getLastReachedCheckpoint().index() < middlePath.index()
          && getLastReachedCheckpoint().isPermanent()) {
        return;
      }
      if (isUnderSecondaryOwnerControl()
          && getLastReachedCheckpoint().index() > middlePath.index()
          && getLastReachedCheckpoint().isPermanent()) {
        return;
      }
    }

    if (currentPath.index() == middlePath.index() && isNeutral()) return;

    Path nextPath = getControllingTeamPath();
    Vector direction = nextPath.getLocation().toVector().subtract(payloadLocation.toVector());
    double len = direction.length(), extraLen = distance - len;
    // If there's extra distance, skip calculations, otherwise, move payload proportionally
    if (extraLen > 0) {
      currentPath = nextPath;
      move(extraLen);
    } else payloadLocation.add(direction.multiply(distance / len));

    payloadEntity.teleport(payloadLocation);

    refreshRegion();
  }

  private PayloadCheckpoint calculateCheckpointContext() {
    PayloadCheckpoint newCheckpoint = null;

      for (PayloadCheckpoint checkpoint : checkpointMap.values()){
        if (currentPath.index == checkpoint.index()){
          newCheckpoint = checkpoint;
        }
      }
    return newCheckpoint;
  }

  // a = x / r
  // r = 3, a = x / 3 = 1.8
  // FIXME wrong formula :facepalm:
  private void tickDisplay() {
    Color color = currentOwner == null ? Color.WHITE : currentOwner.getFullColor();
    for (double angle = 0, angle2 = 0.6 * definition.getRadius(); angle <= 360; angle += angle2) {
      Location base =
          payloadLocation
              .clone()
              .add(
                  new Vector(
                      definition.getRadius() * Math.cos(angle),
                      0.5,
                      definition.getRadius() * Math.sin(angle)));
      match
          .getWorld()
          .spigot()
          .playEffect(
              base,
              Effect.COLOURED_DUST,
              Material.WOOL.getId(),
              (byte) 0, //Does not matter?
              rgbToParticle(color.getRed()),
              rgbToParticle(color.getGreen()),
              rgbToParticle(color.getBlue()),
              1,
              0,
              200);
    }

    // Set the wool block inside the payload to the color of the controlling team
    ChatColor color2 = currentOwner != null ? currentOwner.getColor() : ChatColor.WHITE;
    byte blockData = BukkitUtils.chatColorToDyeColor(color2).getWoolData();
    MaterialData data = payloadEntity.getDisplayBlock();
    data.setData(blockData);
    payloadEntity.setDisplayBlock(data);
  }

  private float rgbToParticle(int rgb) {
    return (float) Math.max(0.001, rgb / 255.0);
  }

  private void updateProx() {} //TODO :(

  public void createPayload() {
    // Order is important!
    makeRail();
    summonMinecart();
    refreshRegion();
  }

  private void summonMinecart() {
    Location minecartSpawn =
        middlePath.getLocation().clone().toCenterLocation().subtract(0, 0.5, 0);

    payloadLocation = minecartSpawn;

    // Set the floor to gold
    Location below = minecartSpawn.clone();
    below.setY(minecartSpawn.getY() - 1);
    below.getBlock().setType(Material.GOLD_BLOCK);

    // Spawn the Payload entity
    Location spawn = minecartSpawn.clone();
    spawn.setYaw(definition.getYaw());
    payloadEntity = minecartSpawn.getWorld().spawn(spawn, Minecart.class);
    ChatColor color = currentOwner != null ? currentOwner.getColor() : COLOR_NEUTRAL_TEAM;
    byte blockData = BukkitUtils.chatColorToDyeColor(color).getWoolData();
    MaterialData payloadBlock = new MaterialData(Material.WOOL);
    payloadBlock.setData(blockData);
    payloadEntity.setDisplayBlock(payloadBlock);
    // this.payloadEntity.setGravity(false);
    payloadEntity.setMaxSpeed(0);
    payloadEntity.setSlowWhenEmpty(true);

    // Summon a label for it
    labelEntity =
        payloadLocation.getWorld().spawn(payloadLocation.clone().add(0, 0.2, 0), ArmorStand.class);
    labelEntity.setVisible(false);
    labelEntity.setGravity(false);
    labelEntity.setRemoveWhenFarAway(false);
    labelEntity.setArms(false);
    labelEntity.setBasePlate(false);
    labelEntity.setCustomName(getColoredName());
    labelEntity.setCustomNameVisible(true);
    labelEntity.setCanPickupItems(false);
  }

  private void refreshRegion() {
    setGoalRegion(
        new CylindricalRegion(
            payloadLocation.toVector(), definition.getRadius(), definition.getHeight()));
  }

  private Path getControllingTeamPath() {

    if (canDominate(currentOwner)) {
      if (isUnderPrimaryOwnerControl()) return currentPath.next();
      if (isUnderSecondaryOwnerControl()) return currentPath.previous();
    }

    return (currentPath.index() > middlePath.index())
        ? currentPath.next()
        : currentPath.previous();
  }

  /** Gets the relevant speed value from the {@link Payload}s {@link PayloadDefinition} */
  private float getControllingTeamSpeed() {
    if (canDominate(currentOwner)) {
      if (isUnderPrimaryOwnerControl()) return definition.getPrimaryOwnerSpeed();
      if (isUnderSecondaryOwnerControl()) return definition.getSecondaryOwnerSpeed();
    }

    // If any non-pushing team controls the payload, neutral speed should be used
    return definition.getNeutralSpeed();
  }

  private boolean isUnderPrimaryOwnerControl() {
    TeamMatchModule tmm = match.needModule(TeamMatchModule.class);
    return currentOwner == tmm.getTeam(definition.getPrimaryOwner());
  }

  private boolean isUnderSecondaryOwnerControl() {
    TeamMatchModule tmm = match.needModule(TeamMatchModule.class);

    // Ensure that if there is no secondary owner (definition.getCompetingOwner = null)
    // this does not return true if the state is neutral (currentOwner = null)
    if (definition.getSecondaryOwner() != null)
      return currentOwner == tmm.getTeam(definition.getSecondaryOwner());
    return false;
  }

  private boolean isNeutral() {
    return currentOwner == null;
  }

  public void registerEvents() {
    match.addListener(playerTracker, MatchScope.RUNNING);
    match.addListener(this, MatchScope.RUNNING);
  }

  public void unregisterEvents() {
    HandlerList.unregisterAll(playerTracker);
    HandlerList.unregisterAll(this);
  }

  protected void makeRail() {
    final Location location = getStartingLocation().toLocation(getMatch().getWorld());

    // Payload must start on a rail
    if (!isRails(location.getBlock().getType())) {
      getMatch().getLogger().warning("No rail found in starting position for payload");
      return;
    }

    final Rails startingRails = (Rails) location.getBlock().getState().getMaterialData();

    if (startingRails.isCurve() || startingRails.isOnSlope()) {
      getMatch().getLogger().warning("Starting rail can not be either curved or in a slope");
      return;
    }

    if (isCheckpoint(location.getBlock())) {
      getMatch().getLogger().warning("Starting rail can not be a checkpoint");
      return;
    }

    BlockFace direction = startingRails.getDirection();

    final List<Double> differingX = new ArrayList<>();
    final List<Double> differingY = new ArrayList<>();
    final List<Double> differingZ = new ArrayList<>();

    differingY.add(0.0);
    differingY.add(1.0);
    differingY.add(-1.0);

    headPath = new Path(railSize, location, null, null);
    railSize++;

    Path previousPath = headPath;
    Path neighborRail =
        getNewNeighborPath(previousPath, direction, differingX, differingY, differingZ);

    while (neighborRail != null) {

      previousPath.setNext(neighborRail);

      previousPath = neighborRail;

      differingX.clear();
      differingZ.clear();

      if (previousPath.getLocation().getBlock().getState().getMaterialData() instanceof Rails) {
        direction =
            ((Rails) previousPath.getLocation().getBlock().getState().getMaterialData())
                .getDirection();
      } else {
        direction = null;
      }

      neighborRail =
          getNewNeighborPath(previousPath, direction, differingX, differingY, differingZ);
    }

    tailPath = previousPath;

    Path currentPath = headPath;
    Path lastPath = null;

    headPath = null;

    boolean moreRails = currentPath.hasNext();
    while (moreRails) {

      Path nextPath = currentPath.next();
      Location newLocation =
          currentPath
              .getLocation()
              .toVector()
              .midpoint(nextPath.getLocation().toVector())
              .toLocation(getMatch().getWorld());
      newLocation.setY(Math.max(currentPath.getLocation().getY(), nextPath.getLocation().getY()));
      Path newPath;
      if (headPath == null) {
        Location headLocation =
            newLocation.clone().add(currentPath.getLocation().subtract(nextPath.getLocation()));
        headPath = new Path(railSize, headLocation, null, null);
        railSize++;
        newPath = new Path(railSize, newLocation, headPath, null);
        railSize++;
        headPath.setNext(newPath);
        lastPath = newPath;
        this.currentPath = headPath;
      } else {
        newPath = new Path(railSize, newLocation, lastPath, null, currentPath.isCheckpoint());
        railSize++;
        lastPath.setNext(newPath);
        lastPath = newPath;
        tailPath = lastPath;
      }

      currentPath = nextPath;
      moreRails = currentPath.hasNext();
    }

    Path tail = tailPath;
    Path beforeTail = tail.previous();
    Location newLocation =
        tail.getLocation()
            .toVector()
            .midpoint(beforeTail.getLocation().toVector())
            .toLocation(getMatch().getWorld());
    newLocation.setY(Math.max(tail.getLocation().getY(), beforeTail.getLocation().getY()));
    Location tailLocation =
        newLocation.clone().add(currentPath.getLocation().subtract(beforeTail.getLocation()));
    tailPath = new Path(tailLocation, tail, null);
    tail.setNext(tailPath);

    // Finding the middle path
    // 99 / 2 = 45 + 4,5 = 49,5 -> middle number is 50, 50 - 49 = 1, 50 + 49 = 99,
    // 100 /2 = 50 -> middle number is 50,5, 50,5 + 49,5 = 100, 50,5 - 49,5 = 1

    // TODO: support rails with even sizes
    // TODO: test this lol
    // if(railSize % 2 == 0) match.sendMessage("Payload does not currently support rails with an
    // even size");
    double lookingFor = railSize / 2D;
    lookingFor += 1;
    Path discoverMiddle = tail;
    while (discoverMiddle.hasPrevious()) {
      if ((double) discoverMiddle.index() == lookingFor) {
        middlePath = discoverMiddle;
        break;
      }
      discoverMiddle = discoverMiddle.previous();
    }

    this.currentPath = middlePath;

    // Checkpoint calculation

    final List<Integer> permanentHead = definition.getPermanentHeadCheckpoints();
    final List<Integer> permanentTail = definition.getPermanentTailCheckpoints();

    Path discoverCheckpoints = middlePath;
    int h = 0;
    while (discoverCheckpoints.hasNext()) {
      Path potentialCheckpoint =
          discoverCheckpoints.next(); // No reason to check for a checkpoint ON the middle path
      if (potentialCheckpoint.isCheckpoint()) {
        int index = potentialCheckpoint.index();
        boolean permanent = false;
        if (permanentHead != null) permanent = permanentHead.contains(checkpointMap.size() + 1);
        checkpointMap.put(h, new PayloadCheckpoint(index, h, permanent));
        h++;
      }
      discoverCheckpoints = discoverCheckpoints.next();
    }

    //Adding the goal as a checkpoint for sidebar progress rendering
    checkpointMap.put(h, new PayloadCheckpoint(discoverCheckpoints.index(), h, false));

    discoverCheckpoints = middlePath; // Reset

    final int offset = checkpointMap.size();

    int t = -1;
    while (discoverCheckpoints.hasPrevious()) {
      Path potentialCheckpoint = discoverCheckpoints.previous();
      if (potentialCheckpoint.isCheckpoint()) {
        int index = potentialCheckpoint.index();
        boolean permanent = false;
        if (permanentTail != null) permanent = permanentTail.contains(checkpointMap.size() - offset + 1);
        checkpointMap.put(t, new PayloadCheckpoint(index, t, permanent));
        t--;
      }
      discoverCheckpoints = discoverCheckpoints.previous();
    }

    //Adding the goal as a checkpoint for sidebar progress rendering
    checkpointMap.put(t, new PayloadCheckpoint(discoverCheckpoints.index(), t, false));
  }

  public boolean isRails(Material material) {
    return material.equals(Material.RAILS);
  }

  /** The default {@link MaterialMatcher} used for checking if a payload is on a checkpoint */
  public static final MaterialMatcher STANDARD_CHECKPOINT_MATERIALS =
      new CompoundMaterialMatcher(
          ImmutableList.of(
              new SingleMaterialMatcher(Material.DETECTOR_RAIL),
              new SingleMaterialMatcher(Material.ACTIVATOR_RAIL),
              new SingleMaterialMatcher(Material.POWERED_RAIL)));

  /**
   * Checks the given {@link Block} and the block below({@code BlockFace.DOWN}) for any {@link
   * Material}s matching with either a xml-defined {@link MaterialMatcher} or {@code
   * Payload.STANDARD_CHECKPOINT_MATERIALS} by default
   */
  private boolean isCheckpoint(Block block) {
    final MaterialMatcher materialMatcher;

    if (definition.getCheckpointMaterial() != null)
      materialMatcher = definition.getCheckpointMaterial();
    else materialMatcher = STANDARD_CHECKPOINT_MATERIALS;

    return materialMatcher.matches(block.getType())
        || materialMatcher.matches(block.getRelative(BlockFace.DOWN).getType());
  }

  public Path getNewNeighborPath(
      Path path,
      BlockFace direction,
      List<Double> differingX,
      List<Double> differingY,
      List<Double> differingZ) {
    Location previousLocation = null;
    if (path.previous() != null) {
      previousLocation = path.previous().getLocation();
    }

    Location location = path.getLocation();

    if (direction == null) {
      differingX.add(-1.0);
      differingX.add(0.0);
      differingX.add(1.0);
      differingZ.add(-1.0);
      differingZ.add(0.0);
      differingZ.add(1.0);
    } else if (direction.equals(BlockFace.SOUTH) || direction.equals(BlockFace.NORTH)) {
      differingZ.add(-1.0);
      differingZ.add(1.0);
      differingX.add(0.0);
    } else if (direction.equals(BlockFace.EAST) || direction.equals(BlockFace.WEST)) {
      differingX.add(-1.0);
      differingX.add(1.0);
      differingZ.add(0.0);
    } else {
      Location side = location.clone();
      side.setZ(
          side.getZ()
              + (direction.equals(BlockFace.NORTH_WEST) || direction.equals(BlockFace.NORTH_EAST)
                  ? 1
                  : -1));
      if (side.getX() == previousLocation.getX() && side.getZ() == previousLocation.getZ()) {
        differingX.add(
            direction.equals(BlockFace.SOUTH_WEST) || direction.equals(BlockFace.NORTH_WEST)
                ? 1.0
                : -1.0);
        differingZ.add(0.0);
      } else {
        differingX.add(0.0);
        differingZ.add(
            direction.equals(BlockFace.NORTH_WEST) || direction.equals(BlockFace.NORTH_EAST)
                ? 1.0
                : -1.0);
      }
    }

    Location newLocation = location.clone();
    for (double x : differingX) {
      for (double y : differingY) {
        for (double z : differingZ) {
          newLocation.add(x, y, z);

          boolean isCheckpoint = isCheckpoint(newLocation.getBlock());

          if (isRails(newLocation.getBlock().getType()) || isCheckpoint) {
            Path currentPath = path;
            if (currentPath.equals(headPath)) {
              return new Path(newLocation, path, null, isCheckpoint);
            }

            boolean alreadyExists = false;
            while (currentPath.hasPrevious()) {
              if (equalsVectorCoordinates(
                  currentPath.getLocation().toVector(), newLocation.toVector())) {
                alreadyExists = true;
                break;
              }
              currentPath = currentPath.previous();
            }

            if (!alreadyExists) {
              return new Path(newLocation, path, null, isCheckpoint);
            }
          }

          newLocation.subtract(x, y, z);
        }
      }
    }
    return null;
  }

  private boolean equalsVectorCoordinates(
      Vector one, Vector two) { // TODO: replace with Vector.equals() ?
    return one.getX() == two.getX() && one.getY() == two.getY() && one.getZ() == two.getZ();
  }

  // Listener

  // Since 1.8 spigot does not have #setInvulnerable these methods protect the entity instead
  @EventHandler
  public void onVehicleDamage(final VehicleDamageEvent event) {
    if (event.getVehicle() == payloadEntity) event.setCancelled(true);
  }

  @EventHandler
  public void onVehicleEnter(final VehicleEnterEvent event) {
    if (event.getVehicle() == payloadEntity) event.setCancelled(true);
  }

  @EventHandler
  public void onVehicleDestroy(final VehicleDestroyEvent event) {
    if (event.getVehicle() == payloadEntity) event.setCancelled(true);
  }
}
