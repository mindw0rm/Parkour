package io.github.a5h73y.parkour.listener;

import io.github.a5h73y.parkour.Parkour;
import io.github.a5h73y.parkour.enums.ParkourMode;
import io.github.a5h73y.parkour.other.AbstractPluginReceiver;
import io.github.a5h73y.parkour.type.kit.ParkourKit;
import io.github.a5h73y.parkour.type.kit.ParkourKitAction;
import io.github.a5h73y.parkour.type.player.ParkourSession;
import java.util.Arrays;
import java.util.List;
import com.cryptomorin.xseries.XBlock;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class PlayerMoveListener extends AbstractPluginReceiver implements Listener {

    private static final List<BlockFace> BLOCK_FACES =
            Arrays.asList(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    public PlayerMoveListener(final Parkour parkour) {
        super(parkour);
    }

    @EventHandler
    public void onPlayerMove_ParkourMode(PlayerMoveEvent event) {
        if (!parkour.getPlayerManager().isPlaying(event.getPlayer())) {
            return;
        }

        ParkourMode courseMode = parkour.getPlayerManager().getParkourSession(event.getPlayer()).getParkourMode();

        if (courseMode == ParkourMode.NONE) {
            return;
        }

        if (courseMode == ParkourMode.POTION) {
            // check they still have the potion effect, if not reapply it
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!parkour.getPlayerManager().isPlaying(event.getPlayer())) {
            return;
        }

        Player player = event.getPlayer();
        ParkourSession session = parkour.getPlayerManager().getParkourSession(player);

        // Only do fall checks if mode is not 'dropper' course
        if (session.getParkourMode() != ParkourMode.DROPPER
                && player.getFallDistance() > parkour.getConfig().getMaxFallTicks()) {
            parkour.getPlayerManager().playerDie(player);
            return;
        }

        if (player.getLocation().getBlock().isLiquid()
                && parkour.getConfig().getBoolean("OnCourse.DieInLiquid")) {
            parkour.getPlayerManager().playerDie(player);
        }

        if (!parkour.getConfig().isUseParkourKit()) {
            return;
        }

        if (parkour.getConfig().isAttemptLessChecks()) {
            if (event.getTo().getBlockX() == event.getFrom().getBlockX()
                    && event.getTo().getBlockY() == event.getFrom().getBlockY()
                    && event.getTo().getBlockZ() == event.getFrom().getBlockZ()) {
                return;
            }
        }

        Material belowMaterial = player.getLocation().getBlock().getRelative(BlockFace.DOWN).getType();

        // if player is on half-block or jumping, get actual location.
        if (!parkour.getConfig().isLegacyGroundDetection()
                && (!XBlock.isAir(player.getLocation().getBlock().getType()) || !player.isOnGround())) {
            belowMaterial = player.getLocation().getBlock().getType();
        }

        ParkourKit kit = session.getCourse().getParkourKit();

        if (belowMaterial.equals(Material.SPONGE)) {
            player.setFallDistance(0);
        }

        ParkourKitAction kitAction = kit.getAction(belowMaterial);

        if (kitAction != null) {
            switch (kitAction.getActionType()) {
                case FINISH:
                    parkour.getPlayerManager().finishCourse(player);
                    break;

                case DEATH:
                    parkour.getPlayerManager().playerDie(player);
                    break;

                case LAUNCH:
                    player.setVelocity(new Vector(0, kitAction.getStrength(), 0));
                    break;

                case BOUNCE:
                    if (!player.hasPotionEffect(PotionEffectType.JUMP)) {
                        player.addPotionEffect(
                                new PotionEffect(PotionEffectType.JUMP,
                                        kitAction.getDuration(),
                                        (int) kitAction.getStrength()));
                    }
                    break;

                case SPEED:
                    if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
                        player.addPotionEffect(
                                new PotionEffect(PotionEffectType.SPEED,
                                        kitAction.getDuration(),
                                        (int) kitAction.getStrength()));
                    }
                    break;

                case NORUN:
                    player.setSprinting(false);
                    break;

                case NOPOTION:
                    for (PotionEffect effect : player.getActivePotionEffects()) {
                        player.removePotionEffect(effect.getType());
                    }

                    player.setFireTicks(0);
                    break;
            }
        } else {
            for (BlockFace blockFace : BLOCK_FACES) {
                Material material = player.getLocation().getBlock().getRelative(blockFace).getType();
                kitAction = kit.getAction(material);

                if (kitAction != null) {
                    switch (kitAction.getActionType()) {
                        case CLIMB:
                            if (!player.isSneaking()) {
                                player.setVelocity(new Vector(0, kitAction.getStrength(), 0));
                            }
                            break;

                        case REPULSE:
                            double strength = kitAction.getStrength();
                            double x = blockFace == BlockFace.NORTH || blockFace == BlockFace.SOUTH ? 0
                                    : blockFace == BlockFace.EAST ? -strength : strength;
                            double z = blockFace == BlockFace.EAST || blockFace == BlockFace.WEST ? 0
                                    : blockFace == BlockFace.NORTH ? strength : -strength;

                            player.setVelocity(new Vector(x, 0.1, z));
                            break;
                    }
                }
            }
        }
    }
}
