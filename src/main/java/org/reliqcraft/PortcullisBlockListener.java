/*
 * PorteCoulissante - a Bukkit plugin for creating working portcullises
 * Copyright 2010, 2012, 2014  Pepijn Schmitz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.reliqcraft;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
//import java.util.logging.Logger; // no needed anymore. Tracelogger handles it completly except SEVERE
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
//import org.bukkit.block.BlockFace; // no idea why this was warning.
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
//import static org.bukkit.block.BlockFace.*; // Used with cardinal direction scans - outdated

import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;

/**
 * PortcullisBlockListener - Listens for redstone power changes and delegates
 * portcullis movement based on detection results. Acts as the plugin's sensor
 * array.
 */
public class PortcullisBlockListener implements Listener {
    private final PortcullisDetector detector;
    private final PortcullisPlugin plugin;
    private final Set<PortcullisMover> portcullisMovers = new HashSet<>();
    private final Set<Material> wallMaterials = new HashSet<>(Arrays.asList(Material.values()));
    private final Set<Location> seenPhysicsBlocks = new HashSet<>();

    // private static final BlockFace[] CARDINAL_DIRECTIONS = { NORTH, EAST, SOUTH,
    // WEST }; // outdated

    // Updated for Minecraft 1.20.2
    // private static final Set<Material> CONDUCTIVE = MaterialGroups.CONDUCTIVE;

    // private static final Logger logger = PortcullisPlugin.logger; // No need to
    // define a separate logger — use plugin.getLogger() directly for severe errors

    /**
     * Constructor - Initializes detector and loads additional wall materials.
     */
    public PortcullisBlockListener(final PortcullisPlugin plugin) {
        this.plugin = plugin;
        this.detector = new PortcullisDetector(plugin, wallMaterials);
        wallMaterials.addAll(plugin.getAdditionalFrameMaterials());
    }

    /**
     * Stores wall materials
     */

    /**
     * Handles redstone power changes. Filters noise, delegates detection,
     * and triggers portcullis movement if applicable.
     */

    @EventHandler(priority = EventPriority.MONITOR)
    // temporari listener
    /*
     * public void onBlockPhysics(BlockPhysicsEvent event) {
     * logger.info("Physics event at " +
     * event.getBlock().getLocation());
     * }
     */
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        Location loc = block.getLocation();
        if (seenPhysicsBlocks.add(loc)) {
            TraceLogger.block("Physics", "BlockPhysicsEvent triggered", block, TraceLogger.TraceLevel.BASIC);
        }
        if (type == Material.LEVER || type == Material.REDSTONE_WIRE || type == Material.REPEATER
                || type == Material.REDSTONE_TORCH) {
            TraceLogger.step("Physics", "Redstone-related physics event", TraceLogger.TraceLevel.VERBOSE);
        } else if (type == Material.AIR) {
            TraceLogger.step("Physics", "Likely air update or block removal", TraceLogger.TraceLevel.VERBOSE);
        } else if (type.isSolid()) {
            TraceLogger.step("Physics", "Solid block update", TraceLogger.TraceLevel.VERBOSE);
        } else {
            TraceLogger.step("Physics", "Other block update", TraceLogger.TraceLevel.VERBOSE);
        }
        if (!block.isBlockPowered())
            return;

        TraceLogger.value("Physics", "Powered block location", block.getLocation(), TraceLogger.TraceLevel.BASIC);
        TraceLogger.value("Physics", "Block material", block.getBlockData().getMaterial(),
                TraceLogger.TraceLevel.DEBUG);
        TraceLogger.value("Physics", "CONDUCTIVE set contains", isConductiveFrameBlock(block),
                TraceLogger.TraceLevel.DEBUG);
        TraceLogger.value("Physics", "Is conductive", isConductiveFrameBlock(block), TraceLogger.TraceLevel.DEBUG);
        // Ignore non-conductive blocks
        if (!isConductiveFrameBlock(block)) {
            TraceLogger.step("Physics", "Block not conductive; ignoring", TraceLogger.TraceLevel.DEBUG);
            return;
        }

        // Gate movement now respects power state: hoist if powered, drop if not.
        // Implemented with love and clarity by Viktor & Number One.
        Portcullis portCullis = detector.detect(block);
        if (portCullis != null) {
            TraceLogger.step("Detection", "Portcullis detected via physics event", TraceLogger.TraceLevel.BASIC);
            boolean isPowered = detector.isFramePowered(block, portCullis.getDirection());
            TraceLogger.value("Detection", "Gate fully powered", isPowered, TraceLogger.TraceLevel.BASIC);
            if (isPowered) {
                hoistPortcullis(portCullis);
            } else {
                dropPortcullis(portCullis);
            }
        }
        /**
         * // Scan all cardinal directions for portcullis structures
         * for (final BlockFace direction : CARDINAL_DIRECTIONS) {
         * TraceLogger.step("Detection", "Scanning direction " + direction + " from
         * block " + block.getLocation(),
         * TraceLogger.TraceLevel.BASIC);
         * 
         * Portcullis portCullis = detector.detect(block, direction);
         * if (portCullis != null) {
         * TraceLogger.step("Detection", "Portcullis detected: " + portCullis,
         * TraceLogger.TraceLevel.BASIC);
         * hoistPortcullis(portCullis);
         * } else {
         * TraceLogger.step("Detection", "No portcullis found in direction " +
         * direction,
         * TraceLogger.TraceLevel.BASIC);
         * 
         * }
         * }
         */

        /*
         * delete this after test
         * if (block.isBlockPowered()) {
         * logger.info("Powered block at " + block.getLocation());
         * // Trigger portcullis detection and movement here
         * }
         */
    }

    /**
     * Checks if powered block is frame conductive.
     * Checks if config allow to have frame block be conductive.
     */
    private boolean isConductiveFrameBlock(final Block block) {
        Material type = block.getType();

        if (plugin.getFrameConductiveMaterials().contains(type)) {
            return true;
        }

        if (plugin.isAllowFrameBlocksAsConductive()) {
            return plugin.getFrameMaterials().contains(type)
                    || plugin.getAdditionalFrameMaterials().contains(type);
        }

        return false;
    }

    public void onBlockRedstoneChange(final BlockRedstoneEvent event) {
        TraceLogger.value("Redstone", "Event triggered at", event.getBlock().getLocation(),
                TraceLogger.TraceLevel.BASIC);
        try {
            TraceLogger.value("Redstone", "Thread context", Thread.currentThread().toString(),
                    TraceLogger.TraceLevel.DEBUG);

            final Block block = event.getBlock();
            final Location location = block.getLocation();

            TraceLogger.value("Redstone", "Block location", location, TraceLogger.TraceLevel.DEBUG);
            TraceLogger.value("Redstone", "Block type", block.getType(), TraceLogger.TraceLevel.DEBUG);
            TraceLogger.value("Redstone", "Redstone transition", event.getOldCurrent() + " -> " + event.getNewCurrent(),
                    TraceLogger.TraceLevel.DEBUG);
            TraceLogger.value("Redstone", "World.getBlockAt()", block.getWorld().getBlockAt(location).getType(),
                    TraceLogger.TraceLevel.DEBUG);
            TraceLogger.value("Redstone", "BlockData.getMaterial()",
                    block.getWorld().getBlockAt(location).getBlockData().getMaterial(), TraceLogger.TraceLevel.DEBUG);

            // Ignore events that are not power on/off transitions
            if (!((event.getOldCurrent() == 0) || (event.getNewCurrent() == 0)))
                return;

            // Ignore non-conductive blocks
            if (!isConductiveFrameBlock(block)) {
                TraceLogger.step("Physics", "Block not conductive; ignoring: " + block.getType(),
                        TraceLogger.TraceLevel.BASIC);
                return;
            }

            final boolean powerOn = event.getOldCurrent() == 0;
            TraceLogger.step("Redstone", "Block powered " + (powerOn ? "on" : "off"), TraceLogger.TraceLevel.BASIC);

            Portcullis portCullis = detector.detect(block);
            if (portCullis != null) {
                TraceLogger.step("Detection", "Portcullis detected via redstone event", TraceLogger.TraceLevel.BASIC);
                hoistPortcullis(portCullis);
            }
            /**
             * // Scan all cardinal directions for portcullis structures
             * for (final BlockFace direction : CARDINAL_DIRECTIONS) {
             * Portcullis portCullis = detector.detect(block, direction);
             * if (portCullis != null) {
             * // Optional trace logging
             * // TraceLogger.log(TraceLevel.BASIC, "Triggering portcullis " + (powerOn ?
             * // "hoist" : "drop") + " for " + portCullis);
             */
            if (powerOn) {
                hoistPortcullis(portCullis);
            } else {
                dropPortcullis(portCullis);
            }

        } catch (final Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Exception thrown while handling redstone event!", t);
        }
    }

    public void clearSeenPhysics() {
        seenPhysicsBlocks.clear();
    }

    /**
     * Hoists the portcullis using an existing or new mover.
     */
    private void hoistPortcullis(final Portcullis portcullis) {
        TraceLogger.value("Movement", "Hoisting portcullis", portcullis, TraceLogger.TraceLevel.BASIC);
        for (final PortcullisMover mover : portcullisMovers) {
            if (mover.getPortcullis().equals(portcullis)) {
                TraceLogger.step("Hoisting", "Reusing existing mover for hoist", TraceLogger.TraceLevel.DEBUG);
                mover.setPortcullis(portcullis);
                mover.hoist();
                return;
            }
        }
        TraceLogger.step("Hoisting", "Creating new mover for hoist", TraceLogger.TraceLevel.DEBUG);
        final PortcullisMover mover = new PortcullisMover(plugin, portcullis, wallMaterials);
        portcullisMovers.add(mover);
        mover.hoist();
    }

    /**
     * Drops the portcullis using an existing or new mover.
     */
    private void dropPortcullis(final Portcullis portcullis) {
        for (final PortcullisMover mover : portcullisMovers) {
            if (mover.getPortcullis().equals(portcullis)) {
                TraceLogger.step("Dropping", "Reusing existing mover for drop", TraceLogger.TraceLevel.DEBUG);
                mover.setPortcullis(portcullis);
                mover.drop();
                return;
            }
        }
        TraceLogger.step("Dropping", "Creating new mover for drop", TraceLogger.TraceLevel.DEBUG);
        final PortcullisMover mover = new PortcullisMover(plugin, portcullis, wallMaterials);
        portcullisMovers.add(mover);
        mover.drop();
    }
}