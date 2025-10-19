/*
 * PorteCoulissante - a Bukkit plugin for creating working portcullises
 * Copyright 2010, 2012, 2014  Pepijn Schmitz
 * Licensed under GNU GPL v3
 */
package org.reliqcraft;

import static org.reliqcraft.PortcullisMover.Status.*;

import java.awt.Point;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;

import lombok.Getter;
import lombok.Setter;

/**
 * Handles movement logic for a portcullis structure.
 * Supports hoisting, dropping, and entity movement with trace logging.
 */
public class PortcullisMover implements Runnable {

    // Plugin context for scheduling and configuration
    private final PortcullisPlugin plugin;

    // Custom wall materials allowed for support
    private final Set<Material> wallMaterials;

    // Current portcullis instance being moved
    @Getter
    @Setter
    private Portcullis portcullis;

    // Scheduler task ID for movement
    private int taskId;

    // Current movement status (idle, hoisting, dropping)
    private Status status = IDLE;

    // Legacy logger (used only for severe errors)
    private static final Logger logger = PortcullisPlugin.logger;

    /**
     * Movement status enum.
     */
    static enum Status {
        IDLE, HOISTING, DROPPING
    }

    /**
     * Groups material sets used for movement logic and collision checks.
     */
    public static class MaterialGroups {
        public static final Set<Material> AIR_LIKE = Set.of(
                Material.AIR, Material.WATER, Material.LAVA,
                Material.SUGAR_CANE, Material.SNOW, Material.DANDELION, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM,
                Material.FIRE, Material.WHEAT, Material.TALL_GRASS, Material.COBWEB, Material.PUMPKIN_STEM,
                Material.MELON_STEM, Material.TWISTING_VINES, Material.WEEPING_VINES, Material.LILY_PAD,
                Material.NETHER_WART, Material.CARROTS, Material.POTATOES,
                Material.BAMBOO, Material.BAMBOO_SAPLING, Material.HANGING_ROOTS, Material.MOSS_CARPET,
                Material.SMALL_DRIPLEAF, Material.BIG_DRIPLEAF, Material.SPORE_BLOSSOM, Material.GLOW_LICHEN);

        public static final Set<Material> SUPPORTING = Set.of(
                Material.OAK_FENCE, Material.SPRUCE_FENCE, Material.BIRCH_FENCE, Material.JUNGLE_FENCE,
                Material.ACACIA_FENCE, Material.DARK_OAK_FENCE, Material.CRIMSON_FENCE, Material.WARPED_FENCE,
                Material.BAMBOO_FENCE, Material.MANGROVE_FENCE,
                Material.IRON_BARS, Material.NETHER_BRICK_FENCE,
                Material.COBBLESTONE_STAIRS, Material.NETHER_BRICK_STAIRS, Material.SANDSTONE_STAIRS,
                Material.STONE_BRICK_STAIRS, Material.MANGROVE_STAIRS, Material.BAMBOO_STAIRS);
    }

    /**
     * Constructs a new PortcullisMover instance.
     *
     * @param plugin        The plugin context for scheduling and configuration
     * @param portcullis    The portcullis to be moved
     * @param wallMaterials The set of materials considered valid wall supports
     */
    public PortcullisMover(PortcullisPlugin plugin, Portcullis portcullis, Set<Material> wallMaterials) {
        this.plugin = plugin;
        this.portcullis = portcullis;
        this.wallMaterials = wallMaterials;
    }

    /**
     * Initiates the hoisting sequence for the portcullis.
     * Cancels any active drop task and schedules upward movement.
     * Uses plugin-configured delay and sets status to HOISTING.
     */
    public void hoist() {
        TraceLogger.step("Hoisting", "Hoist requested", TraceLogger.TraceLevel.BASIC);

        if (status == HOISTING) {
            TraceLogger.step("Hoisting", "Already hoisting; ignoring request", TraceLogger.TraceLevel.DEBUG);
            return;
        }

        TraceLogger.step("Hoisting", "Hoisting initiated", TraceLogger.TraceLevel.BASIC);

        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        TraceLogger.step("Hoisting", "Cancelling drop if active", TraceLogger.TraceLevel.DEBUG);

        if (status == DROPPING) {
            scheduler.cancelTask(taskId);
        }

        int hoistingDelay = plugin.getHoistingDelay();
        taskId = scheduler.scheduleSyncRepeatingTask(plugin, this, hoistingDelay / 2, hoistingDelay);

        status = HOISTING;
    }

    /**
     * Initiates the dropping sequence for the portcullis.
     * Cancels any active hoist task and schedules downward movement.
     * Uses plugin-configured delay and sets status to DROPPING.
     */
    public void drop() {
        TraceLogger.step("Dropping", "Drop requested", TraceLogger.TraceLevel.BASIC);

        if (status == DROPPING) {
            TraceLogger.step("Dropping", "Already dropping; ignoring request", TraceLogger.TraceLevel.DEBUG);
            return;
        }

        TraceLogger.step("Dropping", "Dropping initiated", TraceLogger.TraceLevel.BASIC);

        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        TraceLogger.step("Dropping", "Cancelling hoist if active", TraceLogger.TraceLevel.DEBUG);

        if (status == HOISTING) {
            scheduler.cancelTask(taskId);
        }

        int droppingDelay = plugin.getDroppingDelay();
        taskId = scheduler.scheduleSyncRepeatingTask(plugin, this, droppingDelay, droppingDelay);

        status = DROPPING;
    }

    /**
     * Called by Bukkit scheduler to perform a movement tick.
     * Handles hoisting or dropping logic depending on current status.
     * Cancels task and resets status when movement completes.
     */
    @Override
    public void run() {
        TraceLogger.step("Run", "Movement tick triggered", TraceLogger.TraceLevel.BASIC);

        try {
            TraceLogger.value("Run", "Thread context", Thread.currentThread().toString(), TraceLogger.TraceLevel.DEBUG);

            if ((status == HOISTING) && (!movePortcullisUp(portcullis))) {
                plugin.getServer().getScheduler().cancelTask(taskId);
                taskId = 0;
                status = IDLE;
                TraceLogger.step("Hoisting", "Hoisting completed", TraceLogger.TraceLevel.BASIC);
            } else if ((status == DROPPING) && (!movePortcullisDown(portcullis))) {
                plugin.getServer().getScheduler().cancelTask(taskId);
                taskId = 0;
                status = IDLE;
                TraceLogger.step("Dropping", "Dropping completed", TraceLogger.TraceLevel.BASIC);
            }
        } catch (Throwable t) {
            TraceLogger.step("Run", "Exception thrown during movement tick", TraceLogger.TraceLevel.BASIC);
            TraceLogger.value("Run", "Exception details", t.toString(), TraceLogger.TraceLevel.DEBUG);
            logger.log(Level.SEVERE, "[PorteCoulissante] Exception thrown while moving portcullis!", t);
        }

        TraceLogger.value("Run", "Final status after tick", status, TraceLogger.TraceLevel.BASIC);
    }

    /**
     * Moves the portcullis up by one block.
     * Performs world validation, chunk checks, integrity checks, and collision
     * checks.
     * Updates block positions and optionally moves entities.
     *
     * @param portcullis The portcullis to move
     * @return true if movement succeeded, false otherwise
     */
    private boolean movePortcullisUp(final Portcullis portcullis) {
        final World world = plugin.getServer().getWorld(portcullis.getWorldName());
        if (world == null) {
            TraceLogger.step("Hoisting", "World not loaded; cancelling the hoist", TraceLogger.TraceLevel.BASIC);
            return false;
        }

        TraceLogger.value("Hoisting", "World loaded for hoisting", portcullis.getWorldName(),
                TraceLogger.TraceLevel.VERBOSE);

        final int x = portcullis.getX(), y = portcullis.getY(), z = portcullis.getZ();
        final int width = portcullis.getWidth(), height = portcullis.getHeight();
        final BlockFace direction = portcullis.getDirection();

        TraceLogger.value("Hoisting", "Portcullis width", width, TraceLogger.TraceLevel.VERBOSE);
        TraceLogger.value("Hoisting", "Portcullis height", height, TraceLogger.TraceLevel.VERBOSE);
        TraceLogger.value("Hoisting", "Portcullis direction", direction, TraceLogger.TraceLevel.VERBOSE);

        final Set<Point> chunkCoords = getChunkCoords(x, z, direction, width);
        if (!areChunksLoaded(world, chunkCoords)) {
            TraceLogger.step("Hoisting", "Some or all chunks not loaded; cancelling the hoist",
                    TraceLogger.TraceLevel.BASIC);
            return false;
        }

        if (!isPortcullisWhole(world)) {
            TraceLogger.step("Hoisting", "Portcullis no longer intact; cancelling the hoist",
                    TraceLogger.TraceLevel.BASIC);
            return false;
        }

        int clearanceY = y + height;
        int maxY = world.getMaxHeight();
        if (clearanceY >= maxY) {
            TraceLogger.step("Hoisting", "World ceiling reached; destroying portcullis", TraceLogger.TraceLevel.BASIC);
            explodePortcullis(world);
            return false;
        }

        int dx = direction.getModX(), dz = direction.getModZ();
        TraceLogger.value("Hoisting", "Direction modX", dx, TraceLogger.TraceLevel.VERBOSE);
        TraceLogger.value("Hoisting", "Direction modZ", dz, TraceLogger.TraceLevel.VERBOSE);

        if (!plugin.isAllowFloating()) {
            TraceLogger.step("Hoisting", "Floating not allowed; checking for support", TraceLogger.TraceLevel.BASIC);
            boolean solidBlockFound = false;

            for (int yy = y + 1; yy <= y + height; yy++) {
                Material left = world.getBlockAt(x - dx, yy, z - dz).getType();
                Material right = world.getBlockAt(x + width * dx, yy, z + width * dz).getType();

                if (wallMaterials.contains(left) || MaterialGroups.SUPPORTING.contains(left) ||
                        wallMaterials.contains(right) || MaterialGroups.SUPPORTING.contains(right)) {
                    solidBlockFound = true;
                    break;
                }
            }

            if (!solidBlockFound) {
                TraceLogger.step("Hoisting", "Portcullis would be floating; cancelling the hoist",
                        TraceLogger.TraceLevel.BASIC);
                return false;
            }
        }

        for (int i = 0; i < width; i++) {
            int checkX = x + i * dx;
            int checkY = y + height;
            int checkZ = z + i * dz;

            Block block = world.getBlockAt(checkX, checkY, checkZ);
            if (!MaterialGroups.AIR_LIKE.contains(block.getType())) {
                TraceLogger.step("Hoisting", "Not enough room above portcullis (block found @ " + checkX + ", " + checkY
                        + ", " + checkZ + ")", TraceLogger.TraceLevel.BASIC);
                return false;
            }
        }

        TraceLogger.step("Hoisting", "Moving portcullis up one row", TraceLogger.TraceLevel.BASIC);
        Material portcullisType = portcullis.getType();

        for (int i = 0; i < width; i++) {
            int topX = x + i * dx, topY = y + height, topZ = z + i * dz;
            Block blockAbove = world.getBlockAt(topX, topY, topZ);
            blockAbove.setType(portcullisType, true);

            int bottomX = x + i * dx, bottomY = y, bottomZ = z + i * dz;
            Block blockBelow = world.getBlockAt(bottomX, bottomY, bottomZ);
            blockBelow.setType(Material.AIR, true);
        }

        if (plugin.isEntityMovingEnabled()) {
            moveEntitiesUp(world, chunkCoords, portcullis);
        }

        portcullis.setY(y + 1);
        return true;
    }

    /**
     * Moves the portcullis down by one block.
     * Performs world validation, chunk checks, integrity checks, and collision
     * checks.
     * Updates block positions and optionally moves entities.
     *
     * @param portcullis The portcullis to move
     * @return true if movement succeeded, false otherwise
     */
    private boolean movePortcullisDown(Portcullis portcullis) {
        TraceLogger.step("Dropping", "Starting movePortcullisDown", TraceLogger.TraceLevel.BASIC);

        World world = plugin.getServer().getWorld(portcullis.getWorldName());
        if (world == null) {
            TraceLogger.step("Dropping", "World not loaded; cancelling the drop", TraceLogger.TraceLevel.BASIC);
            return false;
        }

        int x = portcullis.getX(), y = portcullis.getY(), z = portcullis.getZ();
        int width = portcullis.getWidth(), height = portcullis.getHeight();
        BlockFace direction = portcullis.getDirection();

        TraceLogger.value("Dropping", "Portcullis X", x, TraceLogger.TraceLevel.VERBOSE);
        TraceLogger.value("Dropping", "Portcullis Y", y, TraceLogger.TraceLevel.VERBOSE);
        TraceLogger.value("Dropping", "Portcullis Z", z, TraceLogger.TraceLevel.VERBOSE);
        TraceLogger.value("Dropping", "Portcullis width", width, TraceLogger.TraceLevel.VERBOSE);
        TraceLogger.value("Dropping", "Portcullis height", height, TraceLogger.TraceLevel.VERBOSE);
        TraceLogger.value("Dropping", "Portcullis direction", direction, TraceLogger.TraceLevel.VERBOSE);

        if (!areChunksLoaded(world, getChunkCoords(x, z, direction, width))) {
            TraceLogger.step("Dropping", "Some or all chunks not loaded; cancelling the drop",
                    TraceLogger.TraceLevel.BASIC);
            return false;
        }

        if (!isPortcullisWhole(world)) {
            TraceLogger.step("Dropping", "Portcullis no longer intact; cancelling the drop",
                    TraceLogger.TraceLevel.BASIC);
            return false;
        }

        if (y <= world.getMinHeight()) {
            TraceLogger.step("Dropping", "Reached world minimum height; no more room to drop",
                    TraceLogger.TraceLevel.BASIC);
            return false;
        }

        int dx = direction.getModX(), dz = direction.getModZ();
        TraceLogger.value("Dropping", "Direction modX", dx, TraceLogger.TraceLevel.VERBOSE);
        TraceLogger.value("Dropping", "Direction modZ", dz, TraceLogger.TraceLevel.VERBOSE);

        for (int i = 0; i < width; i++) {
            int checkX = x + i * dx;
            int checkY = y - 1;
            int checkZ = z + i * dz;

            Block block = world.getBlockAt(checkX, checkY, checkZ);
            if (!MaterialGroups.AIR_LIKE.contains(block.getType())) {
                TraceLogger.step("Dropping", "Block below is not air-like; cancelling drop",
                        TraceLogger.TraceLevel.BASIC);
                return false;
            }
        }

        TraceLogger.step("Dropping", "All checks passed. Entering drop movement loop", TraceLogger.TraceLevel.BASIC);
        Material portcullisType = portcullis.getType();
        y--;

        for (int i = 0; i < width; i++) {
            int topX = x + i * dx, topY = y + height, topZ = z + i * dz;
            int bottomX = x + i * dx, bottomY = y, bottomZ = z + i * dz;

            Block blockAbove = world.getBlockAt(topX, topY, topZ);
            if (blockAbove != null) {
                blockAbove.setType(Material.AIR, true);
            }

            Block blockBelow = world.getBlockAt(bottomX, bottomY, bottomZ);
            blockBelow.setType(portcullisType, true);
        }

        portcullis.setY(y);
        return true;
    }

    /**
     * Moves entities standing on the portcullis upward by one block.
     * Ensures they remain aligned with the gate as it rises.
     */
    private void moveEntitiesUp(World world, Set<Point> chunkCoords, Portcullis portcullis) {
        for (Point chunkCoord : chunkCoords) {
            Chunk chunk = world.getChunkAt(chunkCoord.x, chunkCoord.y);
            for (Entity entity : chunk.getEntities()) {
                Location location = entity.getLocation();
                if (isOnPortcullis(location, portcullis)) {
                    location.setY(location.getY() + 1);
                    entity.teleport(location);
                }
            }
        }
    }

    /**
     * Checks whether a given location is directly on top of the portcullis.
     *
     * @param location   The entity's location
     * @param portcullis The portcullis structure
     * @return true if the location is on the portcullis, false otherwise
     */
    private boolean isOnPortcullis(final Location location, final Portcullis portcullis) {
        int x = portcullis.getX(), y = portcullis.getY(), z = portcullis.getZ();
        int width = portcullis.getWidth(), height = portcullis.getHeight();
        int x2 = x + portcullis.getDirection().getModX() * width;
        int z2 = z + portcullis.getDirection().getModZ() * width;

        if (x > x2) {
            int tmp = x;
            x = x2;
            x2 = tmp;
        }
        if (z > z2) {
            int tmp = z;
            z = z2;
            z2 = tmp;
        }

        final int locX = location.getBlockX(), locY = location.getBlockY(), locZ = location.getBlockZ();
        return (locX >= x) && (locX <= x2) && (locZ >= z) && (locZ <= z2) && (locY == (y + height));
    }

    /**
     * Checks whether all chunks relevant to the portcullis are loaded.
     *
     * @param world       The world context
     * @param chunkCoords The set of chunk coordinates to check
     * @return true if all chunks are loaded, false otherwise
     */
    private boolean areChunksLoaded(final World world, final Set<Point> chunkCoords) {
        for (final Point point : chunkCoords) {
            if (!world.isChunkLoaded(point.x, point.y)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Calculates which chunks the portcullis spans based on its width and
     * direction.
     *
     * @param x         Base X coordinate
     * @param z         Base Z coordinate
     * @param direction Facing direction of the portcullis
     * @param width     Width of the portcullis
     * @return Set of chunk coordinates
     */
    private Set<Point> getChunkCoords(final int x, final int z, final BlockFace direction, final int width) {
        final Set<Point> chunkCoords = new HashSet<>();
        final int firstChunkX = x >> 4;
        final int firstChunkZ = z >> 4;
        chunkCoords.add(new Point(firstChunkX, firstChunkZ));

        final int secondChunkX = (x + direction.getModX() * (width - 1)) >> 4;
        final int secondChunkZ = (z + direction.getModZ() * (width - 1)) >> 4;

        if ((secondChunkX != firstChunkX) || (secondChunkZ != firstChunkZ)) {
            chunkCoords.add(new Point(secondChunkX, secondChunkZ));
        }

        return chunkCoords;
    }

    /**
     * Checks whether the portcullis is fully intact.
     * Verifies that all blocks match the expected material.
     *
     * @param world The world context
     * @return true if the portcullis is whole, false otherwise
     */
    private boolean isPortcullisWhole(final World world) {
        final int dx = portcullis.getDirection().getModX();
        final int dz = portcullis.getDirection().getModZ();
        final Material portcullisType = portcullis.getType();

        for (int y = portcullis.getY(); y < portcullis.getY() + portcullis.getHeight(); y++) {
            int x = portcullis.getX();
            int z = portcullis.getZ();

            for (int i = 0; i < portcullis.getWidth(); i++) {
                Block block = world.getBlockAt(x, y, z);
                TraceLogger.block("Integrity", "Checking block", block, TraceLogger.TraceLevel.DEBUG);
                if (block.getType() != portcullisType) {
                    TraceLogger.step("Integrity", "Mismatch at " + x + "," + y + "," + z, TraceLogger.TraceLevel.DEBUG);
                    return false;
                }
                x += dx;
                z += dz;
            }
        }

        return true;
    }

    /**
     * Destroys the portcullis by replacing all blocks with air and dropping items.
     * Used when movement exceeds world bounds or integrity fails.
     *
     * @param world The world context
     */
    private void explodePortcullis(final World world) {
        TraceLogger.step("Explosion", "Exploding portcullis blocks", TraceLogger.TraceLevel.BASIC);

        int dx = portcullis.getDirection().getModX();
        int dz = portcullis.getDirection().getModZ();
        final Material portcullisType = portcullis.getType();
        final ItemStack itemStack = new ItemStack(portcullisType, 1);

        for (int y = portcullis.getY(); y < portcullis.getY() + portcullis.getHeight(); y++) {
            int x = portcullis.getX();
            int z = portcullis.getZ();

            for (int i = 0; i < portcullis.getWidth(); i++) {
                final Block block = world.getBlockAt(x, y, z);
                block.setType(Material.AIR, true);
                world.dropItemNaturally(block.getLocation(), itemStack);
                x += dx;
                z += dz;
            }
        }

        TraceLogger.step("Explosion", "Portcullis explosion complete", TraceLogger.TraceLevel.BASIC);
    }

    // End of PortcullisMover class
}
