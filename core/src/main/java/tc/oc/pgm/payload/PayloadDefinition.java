package tc.oc.pgm.payload;

import java.util.List;
import javax.annotation.Nullable;
import org.bukkit.util.Vector;
import tc.oc.pgm.api.feature.FeatureInfo;
import tc.oc.pgm.api.filter.Filter;
import tc.oc.pgm.goals.ControllableGoalDefinition;
import tc.oc.pgm.teams.TeamFactory;
import tc.oc.pgm.util.material.MaterialMatcher;

@FeatureInfo(name = "payload")
class PayloadDefinition extends ControllableGoalDefinition {
  /** Where the path building starts from(Location of the primary goal) */
  private final Vector startingLocation;

  /** The direction the Payload spawns */
  private final float yaw;

  /** The primary owner, pushed toward the primary goal */
  private final TeamFactory primaryOwner;

  // The optional secondary owner, if present they push towards the secondary goal
  // @Nullable
  private final TeamFactory secondaryOwner;

  // TODO: Make the payload accept any region
  /** The radius of the payload (detecting players) */
  private final float radius;

  /** The height of the payload (detecting players) */
  private final float height;

  private final Vector primaryOwnerGoal;

  private final Vector secondaryOwnerGoal;

  /** The material(s) of the checkpoint blocks */
  private final MaterialMatcher checkpointMaterial;

  @Nullable private final List<Integer> permanentHeadCheckpoints;
  @Nullable private final List<Integer> permanentTailCheckpoints;

  /** The speed of the payload when under control of the owning team */
  private final float primaryOwnerSpeed;

  /**
   * Speed multiplier for increasing or decreasing forwards speed based on the number of players on
   * the point when under control of the owning team
   */
  private final float primaryOwnerSpeedMultiplier;

  /** The speed of the payload when under control of the competing owning team */
  private final float secondaryOwnerSpeed;

  /**
   * Speed multiplier for increasing or decreasing backwards speed based on the number of players on
   * the point when under control of the competing owning team
   */
  private final float secondaryOwnerSpeedMultiplier;

  /** The speed of the payload when it is in a neutral state */
  private final float neutralSpeed;

  /** Amount of points given to the team that captures the payload */
  private final float points;

  PayloadDefinition(
      String id,
      String name,
      Boolean required,
      boolean visible,
      Vector startingLocation,
      float yaw,
      Filter controlFilter,
      Filter dominateFilter,
      TeamFactory primaryOwner,
      TeamFactory secondaryOwner,
      CaptureCondition captureCondition,
      float radius,
      float height,
      Vector primaryOwnerGoal,
      Vector secondaryOwnerGoal,
      MaterialMatcher checkpointMaterial,
      List<Integer> permanentHeadCheckpoints,
      List<Integer> permanentTailCheckpoints,
      float primaryOwnerSpeed,
      float primaryOwnerSpeedMultiplier,
      float secondaryOwnerSpeed,
      float secondaryOwnerSpeedMultiplier,
      float neutralSpeed,
      boolean permanent,
      float points,
      boolean showProgress) {
    super(
        id,
        name,
        required,
        visible,
        controlFilter,
        dominateFilter,
        captureCondition,
        permanent,
        showProgress);
    this.startingLocation = startingLocation;
    this.yaw = yaw;
    this.primaryOwner = primaryOwner;
    this.secondaryOwner = secondaryOwner;
    this.radius = radius;
    this.height = height;
    this.primaryOwnerGoal = primaryOwnerGoal;
    this.secondaryOwnerGoal = secondaryOwnerGoal;
    this.checkpointMaterial = checkpointMaterial;
    this.permanentHeadCheckpoints = permanentHeadCheckpoints;
    this.permanentTailCheckpoints = permanentTailCheckpoints;
    this.primaryOwnerSpeed = primaryOwnerSpeed;
    this.primaryOwnerSpeedMultiplier = primaryOwnerSpeedMultiplier;
    this.secondaryOwnerSpeed = secondaryOwnerSpeed;
    this.secondaryOwnerSpeedMultiplier = secondaryOwnerSpeedMultiplier;
    this.neutralSpeed = neutralSpeed;
    this.points = points;
  }

  @Override
  public String toString() { // TODO: Choose which variables to show
    return "PayloadDefinition {name="
        + this.getName()
        + " friendlySpeed="
        + this.getPrimaryOwnerSpeed()
        + " friendlySpeedMultiplier="
        + this.getPrimaryOwnerSpeedMultiplier()
        + " enemySpeed="
        + this.getSecondaryOwnerSpeed()
        + " enemySpeedMultiplier="
        + this.getSecondaryOwnerSpeedMultiplier()
        + " owner="
        + this.primaryOwner
        + " captureCondition="
        + this.getCaptureCondition()
        + " controlFilter="
        + this.getControlFilter()
        + " dominateFilter="
        + this.getDominateFilter()
        + " visible="
        + this.isVisible();
  }

  public float getYaw() {
    return this.yaw;
  }

  public Vector getStartingLocation() {
    return this.startingLocation;
  }

  public float getRadius() {
    return this.radius;
  }

  public float getHeight() {
    return this.height;
  }

  public MaterialMatcher getCheckpointMaterial() {
    return checkpointMaterial;
  }

  public List<Integer> getPermanentHeadCheckpoints() {
    return permanentHeadCheckpoints;
  }

  public List<Integer> getPermanentTailCheckpoints() {
    return permanentTailCheckpoints;
  }

  public float getPrimaryOwnerSpeed() {
    return this.primaryOwnerSpeed;
  }

  public float getSecondaryOwnerSpeed() {
    return this.secondaryOwnerSpeed;
  }

  public float getPoints() {
    return this.points;
  }

  public TeamFactory getPrimaryOwner() {
    return primaryOwner;
  }

  public TeamFactory getSecondaryOwner() {
    return secondaryOwner;
  }

  public float getPrimaryOwnerSpeedMultiplier() {
    return primaryOwnerSpeedMultiplier;
  }

  public float getSecondaryOwnerSpeedMultiplier() {
    return secondaryOwnerSpeedMultiplier;
  }

  public float getNeutralSpeed() {
    return neutralSpeed;
  }

  public Vector getPrimaryOwnerGoal() {
    return primaryOwnerGoal;
  }

  public Vector getSecondaryOwnerGoal() {
    return secondaryOwnerGoal;
  }
}
