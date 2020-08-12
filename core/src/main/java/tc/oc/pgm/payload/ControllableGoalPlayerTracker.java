package tc.oc.pgm.payload;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;
import tc.oc.pgm.api.event.CoarsePlayerMoveEvent;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.goals.ControllableGoal;
import tc.oc.pgm.goals.ControllableGoalDefinition;
import tc.oc.pgm.spawns.events.ParticipantDespawnEvent;
import tc.oc.pgm.util.MatchPlayers;

public class ControllableGoalPlayerTracker<T extends ControllableGoalDefinition>
    implements Listener {
  final Match match;
  final ControllableGoal<T> goal; // TODO Make this support Regions instead of goals with a region
  public final Set<MatchPlayer> players = new HashSet<>();

  public ControllableGoalPlayerTracker(Match match, ControllableGoal<T> goal) {
    this.match = match;
    this.goal = goal;
  }

  public Set<MatchPlayer> getPlayersOnGoal() {
    return players;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerMove(final CoarsePlayerMoveEvent event) {
    this.handlePlayerMove(event.getPlayer(), event.getTo().toVector());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerTeleport(final PlayerTeleportEvent event) {
    this.handlePlayerMove(event.getPlayer(), event.getTo().toVector());
  }

  // Should be used if the region moves to prevent players standing still
  // to never get removed from the region(no move events)
  public void updateNearbyPlayersManual() {
    match
        .getPlayers()
        .forEach(p -> handlePlayerMove(p.getBukkit(), p.getBukkit().getLocation().toVector()));
  }

  private void handlePlayerMove(Player bukkit, Vector to) {
    MatchPlayer player = this.match.getPlayer(bukkit);
    if (!MatchPlayers.canInteract(player)) return;

    if (!player.getBukkit().isDead() && goal.getGoalRegion().contains(to.toBlockVector())) {
      this.players.add(player);
    } else {
      this.players.remove(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerDespawn(final ParticipantDespawnEvent event) {
    players.remove(event.getPlayer());
  }
}
