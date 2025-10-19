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
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import static org.bukkit.block.BlockFace.*;

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

    private static final BlockFace[] CARDINAL_DIRECTIONS = { NORTH, EAST, SOUTH, WEST };

    // Updated for Minecraft 1.20.2
    private static final Set<Material> CONDUCTIVE = MaterialGroups.CONDUCTIVE;

    private static final Logger logger = PortcullisPlugin.logger;

    /**
     * Constructor - Initializes detector and loads additional wall materials.
     */
    public PortcullisBlockListener(final PortcullisPlugin plugin) {
        this.plugin = plugin;
        this.detector = new PortcullisDetector(plugin, wallMaterials);
        wallMaterials.addAll(plugin.getAdditionalWallMaterials());
    }

    /**
     * Handles redstone power changes. Filters noise, delegates detection,
     * and triggers portcullis movement if applicable.
     */

    @EventHandler(priority = EventPriority.MONITOR)
    // temporari listener
    /*
     * public void onBlockPhysics(BlockPhysicsEvent event) {
     * logger.info("[PorteCoulissante] Physics event at " +
     * event.getBlock().getLocation());
     * }
     */
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (!block.isBlockPowered())
            return;

        TraceLogger.value("Physics", "Powered block location", block.getLocation(), TraceLogger.TraceLevel.BASIC);
        TraceLogger.value("Physics", "Block material", block.getBlockData().getMaterial(),
                TraceLogger.TraceLevel.DEBUG);
        TraceLogger.value("Physics", "CONDUCTIVE set contains", CONDUCTIVE, TraceLogger.TraceLevel.DEBUG);
        TraceLogger.value("Physics", "Is conductive", CONDUCTIVE.contains(block.getBlockData().getMaterial()),
                TraceLogger.TraceLevel.DEBUG);
        // Ignore non-conductive blocks
        if (!plugin.getPowerBlocks().contains(block.getBlockData().getMaterial())) {
            logger.fine("[PorteCoulissante] Block not conductive; ignoring");
            return;
        }

        // Scan all cardinal directions for portcullis structures
        for (final BlockFace direction : CARDINAL_DIRECTIONS) {
            TraceLogger.step("Detection", "Scanning direction " + direction + " from block " + block.getLocation(),
                    TraceLogger.TraceLevel.BASIC);
            Portcullis portCullis = detector.detect(block, direction);
            if (portCullis != null) {
                TraceLogger.step("Detection", "Portcullis detected: " + portCullis, TraceLogger.TraceLevel.BASIC);
                hoistPortcullis(portCullis);
            } else {
                TraceLogger.step("Detection", "No portcullis found in direction " + direction,
                        TraceLogger.TraceLevel.BASIC);

            }
        }

        /*
         * delete this after test
         * if (block.isBlockPowered()) {
         * logger.info("[PorteCoulissante] Powered block at " + block.getLocation());
         * // Trigger portcullis detection and movement here
         * }
         */
    }

    public void onBlockRedstoneChange(final BlockRedstoneEvent event) {
        logger.info("[PorteCoulissante] Redstone event triggered at " + event.getBlock().getLocation());
        try {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "[PorteCoulissante] PortcullisBlockListener.onBlockRedstoneChange() (thread: "
                        + Thread.currentThread() + ")", new Throwable());
            }

            final Block block = event.getBlock();
            final Location location = block.getLocation();

            if (logger.isLoggable(Level.FINE)) {
                logger.fine("[PorteCoulissante] Redstone event on block @ " + location.getBlockX() + ", "
                        + location.getBlockY() + ", " + location.getBlockZ() + ", type: " + block.getType() + "; "
                        + event.getOldCurrent() + " -> " + event.getNewCurrent());

                if (logger.isLoggable(Level.FINEST)) {
                    logger.finest("[PorteCoulissante] Type according to World.getBlockAt(): "
                            + block.getWorld().getBlockAt(location).getType());
                    logger.finest("[PorteCoulissante] Type according to BlockData.getMaterial(): "
                            + block.getWorld().getBlockAt(location).getBlockData().getMaterial());
                }
            }

            // Ignore events that are not power on/off transitions
            if (!((event.getOldCurrent() == 0) || (event.getNewCurrent() == 0)))
                return;

            // Ignore non-conductive blocks
            if (!CONDUCTIVE.contains(block.getBlockData().getMaterial())) {
                logger.fine("[PorteCoulissante] Block @ " + location.getBlockX() + ", " + location.getBlockY() + ", "
                        + location.getBlockZ() + ", type: " + block.getType() + " not conductive; ignoring");
                return;
            }

            final boolean powerOn = event.getOldCurrent() == 0;
            logger.fine("[PorteCoulissante] Block powered " + (powerOn ? "on" : "off"));

            // Scan all cardinal directions for portcullis structures
            for (final BlockFace direction : CARDINAL_DIRECTIONS) {
                Portcullis portCullis = detector.detect(block, direction);
                if (portCullis != null) {
                    // Optional trace logging
                    // TraceLogger.log(TraceLevel.BASIC, "Triggering portcullis " + (powerOn ?
                    // "hoist" : "drop") + " for " + portCullis);

                    if (powerOn) {
                        hoistPortcullis(portCullis);
                    } else {
                        dropPortcullis(portCullis);
                    }
                }
            }
        } catch (final Throwable t) {
            logger.log(Level.SEVERE, "[PorteCoulissante] Exception thrown while handling redstone event!", t);
        }
    }

    /**
     * Hoists the portcullis using an existing or new mover.
     */
    private void hoistPortcullis(final Portcullis portcullis) {
        logger.info("[PorteCoulissante] Hoisting portcullis: " + portcullis);
        for (final PortcullisMover mover : portcullisMovers) {
            if (mover.getPortcullis().equals(portcullis)) {
                logger.fine("[PorteCoulissante] Reusing existing portcullis mover");
                mover.setPortcullis(portcullis);
                mover.hoist();
                return;
            }
        }
        logger.fine("[PorteCoulissante] Creating new portcullis mover");
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
                logger.fine("[PorteCoulissante] Reusing existing portcullis mover");
                mover.setPortcullis(portcullis);
                mover.drop();
                return;
            }
        }
        logger.fine("[PorteCoulissante] Creating new portcullis mover");
        final PortcullisMover mover = new PortcullisMover(plugin, portcullis, wallMaterials);
        portcullisMovers.add(mover);
        mover.drop();
    }
}