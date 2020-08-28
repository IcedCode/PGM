package tc.oc.pgm.payload;

import java.util.List;
import java.util.stream.Collectors;
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

  /** The relative "middle" of the payload path(used for checkpoints and neutral movement)*/
  private final Vector middleLocation;

  /** The direction the Payload spawns */
  private final float yaw;

  /** The primary owner, pushed toward the primary goal */
  private final TeamFactory primaryOwner;

  // The optional secondary owner, if present they push towards the secondary goal
  @Nullable
  private final TeamFactory secondaryOwner;

  // TODO: Make the payload accept any region
  /** The radius of the payload (detecting players) */
  private final float radius;

  /** The height of the payload (detecting players) */
  private final float height;

  /** Determines if the secondary team should be able to push but have no goal */
  private final boolean secondaryTeamPushButNoGoal;

  /** The material(s) of the checkpoint blocks */
  @Nullable private final MaterialMatcher checkpointMaterial;

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
          Vector middleLocation,
          float yaw,
          Filter controlFilter,
          Filter dominateFilter,
          TeamFactory primaryOwner,
          @Nullable TeamFactory secondaryOwner,
          CaptureCondition captureCondition,
          float radius,
          float height,
          boolean secondaryTeamPushButNoGoal,
          @Nullable MaterialMatcher checkpointMaterial,
          @Nullable List<Integer> permanentHeadCheckpoints,
          @Nullable List<Integer> permanentTailCheckpoints,
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
    this.middleLocation = middleLocation;
    this.yaw = yaw;
    this.primaryOwner = primaryOwner;
    this.secondaryOwner = secondaryOwner;
    this.radius = radius;
    this.height = height;
    this.secondaryTeamPushButNoGoal = secondaryTeamPushButNoGoal;
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

  public float getYaw() {
    return this.yaw;
  }

  public Vector getStartingLocation() {
    return this.startingLocation;
  }

  public Vector getMiddleLocation() {
    return middleLocation;
  }

  public float getRadius() {
    return this.radius;
  }

  public float getHeight() {
    return this.height;
  }

  @Nullable
  public MaterialMatcher getCheckpointMaterial() {
    return checkpointMaterial;
  }

  @Nullable
  public List<Integer> getPermanentHeadCheckpoints() {
    return permanentHeadCheckpoints;
  }

  @Nullable
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

  @Nullable
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

  public boolean shouldSecondaryTeamPushButNoGoal() {
    return secondaryTeamPushButNoGoal;
  }

  @Override
  public String toString() {
    return "PayloadDefinition{" +
            "startingLocation=" + startingLocation +
            ", yaw=" + yaw +
            ", primaryOwner=" + primaryOwner +
            ", secondaryOwner=" + secondaryOwner +
            ", radius=" + radius +
            ", height=" + height +
            ", secondaryTeamPushButNoGoal=" + secondaryTeamPushButNoGoal +
            ", checkpointMaterial=" + checkpointMaterial +
            ", permanentHeadCheckpoints=" + permanentHeadCheckpoints +
            ", permanentTailCheckpoints=" + permanentTailCheckpoints +
            ", primaryOwnerSpeed=" + primaryOwnerSpeed +
            ", primaryOwnerSpeedMultiplier=" + primaryOwnerSpeedMultiplier +
            ", secondaryOwnerSpeed=" + secondaryOwnerSpeed +
            ", secondaryOwnerSpeedMultiplier=" + secondaryOwnerSpeedMultiplier +
            ", neutralSpeed=" + neutralSpeed +
            ", points=" + points +
            '}';
  }
}
