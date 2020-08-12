package tc.oc.pgm.payload;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.*;
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
import tc.oc.pgm.regions.CylindricalRegion;
import tc.oc.pgm.score.ScoreMatchModule;
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
// -------------------------------------------------------------------

public class Payload extends ControllableGoal<PayloadDefinition>
    implements IncrementalGoal<PayloadDefinition>, Listener {

  public static final ChatColor COLOR_NEUTRAL_TEAM = ChatColor.WHITE;

  public static final String SYMBOL_PAYLOAD_NEUTRAL = "\u29be"; // ⦾
  public static final String SYMBOL_PAYLOAD_PUSHING = "\u279f"; // ➟

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

  private Path headPath;
  private Path tailPath;

  private Path currentPath;

  // The path the payload goes towards in the neutral state, the path the payload starts on;
  private Path middlePath;

  // TODO save the furhtest each team has come for eom calculation

  // Switches to true when the payload reaches a end
  private boolean completed = false;

  private final List<PayloadCheckpoint> headCheckpoints = new ArrayList<>();
  private final List<PayloadCheckpoint> tailCheckpoints = new ArrayList<>();

  private PayloadCheckpoint lastReachedCheckpoint;

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
    return "o----o";
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
          team) { // TODO: Change this if optional goals for primary/secondary team is added (Check
    // if goal exists)
    if (team instanceof Team)
      return (((Team) team).isInstance(definition.getPrimaryOwner())
              && definition.getPrimaryOwnerGoal() != null)
          || (((Team) team).isInstance(definition.getSecondaryOwner())
              && definition.getSecondaryOwnerGoal() != null);
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
    return isNeutral() ? ChatColor.GRAY : currentOwner.getColor();
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
  }

  private void tickMove() {

    float speed = getControllingTeamSpeed(); // Fetches the speed for the current owner

    Path finalPath =
        isUnderPrimaryOwnerControl()
            ? headPath
            : tailPath; // Fetches the final path for the current owner (head or tail)

    Location finalLocation = finalPath.getLocation();
    final float points = definition.getPoints(); // TODO: Make Completable instead of point based

    if (payloadLocation.getX() == finalLocation.getX()
        && payloadLocation.getZ() == finalLocation.getZ()) {
      match.sendMessage(TextComponent.of("Something wrong here..."));
      if (points > 0) {
        completed = true;
        match.callEvent(new GoalCompleteEvent(match, this, currentOwner, true));
        match.sendMessage(
            TextComponent.of(
                currentOwner.getNameLegacy() + " Completed the goal 8)", TextColor.GOLD));

        final ScoreMatchModule smm = match.needModule(ScoreMatchModule.class);
        if (smm != null) {
          if (currentOwner != null) smm.incrementScore(currentOwner, points);
        }
        return;
      }
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
        && (lastReachedCheckpoint == null
            || currentPath.getIndex() != lastReachedCheckpoint.getIndex())) {
      PayloadCheckpoint newCheckpoint = null;

      if (currentPath.getIndex() > middlePath.getIndex()) {
        if (lastReachedCheckpoint == tailCheckpoints.get(0) || lastReachedCheckpoint == null)
          newCheckpoint = headCheckpoints.get(0);
        else
          for (PayloadCheckpoint headCheckpoint : headCheckpoints) {
            if (currentPath.getIndex() == headCheckpoint.getIndex()) {
              newCheckpoint = headCheckpoint;
            }
          }
      } else {
        if (lastReachedCheckpoint == headCheckpoints.get(0) || lastReachedCheckpoint == null)
          newCheckpoint = tailCheckpoints.get(0);
        else
          for (PayloadCheckpoint tailCheckpoint : tailCheckpoints) {
            if (currentPath.getIndex() == tailCheckpoint.getIndex()) {
              newCheckpoint = tailCheckpoint;
            }
          }
      }

      if (newCheckpoint == null) return;

      if (lastReachedCheckpoint != newCheckpoint) {
        final Component message =
            TranslatableComponent.of(
                "payloadCheckpoint",
                TextColor.GRAY,
                currentOwner == null
                    ? TextComponent.of("Unknown", TextColor.DARK_AQUA)
                    : currentOwner.getName(), // Put in the owner of the checkpoint
                TextComponent.of(Math.abs(0)));
        match.sendMessage(
            TextComponent.of(
                "Checkpoint at index "
                    + newCheckpoint.getIndex()
                    + "With last checkpoint "
                    + (lastReachedCheckpoint == null ? "null" : lastReachedCheckpoint.getIndex())));
      }

      lastReachedCheckpoint = newCheckpoint;
    }

    if (currentPath.isCheckpoint() && lastReachedCheckpoint != null) {
      if (isUnderPrimaryOwnerControl()
          && lastReachedCheckpoint.getIndex() < middlePath.getIndex()
          && lastReachedCheckpoint.isPermanent()) {
        // match.sendMessage(TextComponent.of("primarycontrol + lastindex < middleindex + " +
        // lastReachedCheckpoint.getIndex() + " is a PERMANENT CHECKPOINT"));
        return;
      }
      if (isUnderSecondaryOwnerControl()
          && lastReachedCheckpoint.getIndex() > middlePath.getIndex()
          && lastReachedCheckpoint.isPermanent()) {
        // match.sendMessage(TextComponent.of("secondarycontrol + lastindex > middleindex + " +
        // lastReachedCheckpoint.getIndex() + " is a PERMANENT CHECKPOINT"));
        return;
      }
    }

    if (currentPath.getIndex() == middlePath.getIndex() && isNeutral()) return;

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

  // a = 0,6 * r
  // r = 3, a = 0,6 * 3 = 1.8
  // FIXME wrong formula :facepalm:
  private void tickDisplay() {
    Color color = currentOwner == null ? Color.WHITE : currentOwner.getFullColor();
    ChatColor controllingColor =
        currentOwner != null ? currentOwner.getColor() : COLOR_NEUTRAL_TEAM;
    for (double angle = 0, angle2 = 0.6 * definition.getRadius(); angle <= 360; angle += angle2) {
      Location base =
          payloadLocation
              .clone()
              .add(
                  new Vector(
                      definition.getRadius() * Math.cos(angle),
                      0.5,
                      definition.getRadius() * Math.sin(angle)));
      String asuh =
          controllingColor == ChatColor.DARK_RED ? ChatColor.RED.name() : controllingColor.name();
      match
          .getWorld()
          .spigot()
          .playEffect(
              base,
              Effect.COLOURED_DUST,
              Material.WOOL.getId(), // TODO is these two required???
              DyeColor.valueOf(asuh).getWoolData(),
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

  public void createPayload() {
    // Order is important!
    makeRail();
    summonMinecart();
    refreshRegion();
  }

  private void summonMinecart() {
    Location minecartSpawn = middlePath.getLocation();

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

    return (currentPath.getIndex() > middlePath.getIndex())
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
    Location location = getStartingLocation().toLocation(getMatch().getWorld());

    // Payload must start on a rail
    if (!isRails(location.getBlock().getType())) {
      getMatch().getLogger().warning("No rail found in starting position for payload");
      return; // TODO: break with moduleloadexception?
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
      // if(neighborRail.isCheckpoint()) checkpoints.add(neighborRail.getIndex());

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
    double lookingfor = railSize / 2D;
    Path a = tail;
    while (a.hasPrevious()) {
      if ((double) a.getIndex() == lookingfor) {
        middlePath = a;
        break;
      }
      a = a.previous();
    }

    this.currentPath = middlePath;

    // Checkpoint calculation

    final List<Integer> permanentHead = definition.getPermanentHeadCheckpoints();
    final List<Integer> permanentTail = definition.getPermanentTailCheckpoints();

    Path b = middlePath;
    while (b.hasNext()) {
      Path potentialCheckpoint = b.next(); // No reason to check for a checkpoint ON the middle path
      if (potentialCheckpoint.isCheckpoint()) {
        int index = potentialCheckpoint.getIndex();
        boolean permanent = false;
        if (permanentHead != null) permanent = permanentHead.contains(headCheckpoints.size() + 1);
        headCheckpoints.add(new PayloadCheckpoint(index, permanent));
      }
      b = b.next();
    }

    b = middlePath; // Reset

    while (b.hasPrevious()) {
      Path potentialCheckpoint = b.previous();
      if (potentialCheckpoint.isCheckpoint()) {
        int index = potentialCheckpoint.getIndex();
        boolean permanent = false;
        if (permanentTail != null) permanent = permanentTail.contains(tailCheckpoints.size() + 1);
        tailCheckpoints.add(new PayloadCheckpoint(index, permanent));
      }
      b = b.previous();
    }
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
