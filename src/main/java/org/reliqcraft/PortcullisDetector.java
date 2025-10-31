package org.reliqcraft;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * PortcullisDetector - Responsible for detecting valid portcullis structures
 * adjacent to redstone-powered blocks. Encapsulates structural validation logic
 * and normalization of coordinates.
 */
public class PortcullisDetector {
    private final PortcullisPlugin plugin;
    private final Set<Material> wallMaterials;
    private static final Logger logger = PortcullisPlugin.logger;

    public PortcullisDetector(PortcullisPlugin plugin, Set<Material> wallMaterials) {
        this.plugin = plugin;
        this.wallMaterials = wallMaterials;

    }

    /**
     * Attempts to detect a valid portcullis structure in the given direction.
     * If found, normalizes its coordinates and logs details.
     */
    public Portcullis detect(Block block, BlockFace direction) {
        TraceLogger.step("Detection1", "detect() called", TraceLogger.TraceLevel.BASIC);
        TraceLogger.block("Detection1", "Redstone-powered block", block, TraceLogger.TraceLevel.DEBUG);
        Portcullis portCullis = findPortcullisInDirection(block, direction);
        if (portCullis != null) {
            // logger.info("[PorteCoulissante] Raw portcullis: " + portCullis);
            portCullis = normalisePortcullis(portCullis);
            // logger.info("[PorteCoulissante] Normalized portcullis: " + normalized);
            logPortcullisDetails(portCullis, block.getWorld());
        }
        return portCullis;
    }

    /**
     * Logs detailed structure of the detected portcullis for debugging.
     */
    private void logPortcullisDetails(Portcullis portCullis, World world) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("[PorteCoulissante] Portcullis found! (x: " + portCullis.getX() + ", z: "
                    + portCullis.getZ() + ", y: " + portCullis.getY() + ", width: " + portCullis.getWidth()
                    + ", height: " + portCullis.getHeight() + ", direction: " + portCullis.getDirection() + ")");
        }
        if (logger.isLoggable(Level.FINEST)) {
            for (int y = portCullis.getY() + portCullis.getHeight() + 4; y >= portCullis.getY() - 5; y--) {
                StringBuilder sb = new StringBuilder("[PorteCoulissante] ");
                sb.append(y);
                for (int i = -5; i <= portCullis.getWidth() + 4; i++) {
                    sb.append('|');
                    sb.append(world.getBlockAt(
                            portCullis.getX() + i * portCullis.getDirection().getModX(), y,
                            portCullis.getZ() + i * portCullis.getDirection().getModZ())
                            .getBlockData().getMaterial().toString());
                }
                logger.finest(sb.toString());
            }
        }
    }

    // Normalizes coordinates for WEST/NORTH-facing gates to ensure consistent
    // movement.

    private Portcullis normalisePortcullis(final Portcullis portcullis) {
        switch (portcullis.getDirection()) {
            case WEST:
                return new Portcullis(portcullis.getWorldName(), portcullis.getX() -
                        portcullis.getWidth() + 1,
                        portcullis.getZ(), portcullis.getY(), portcullis.getWidth(),
                        portcullis.getHeight(),
                        BlockFace.EAST, portcullis.getType());
            case NORTH:
                return new Portcullis(portcullis.getWorldName(), portcullis.getX(),
                        portcullis.getZ() - portcullis.getWidth() + 1, portcullis.getY(),
                        portcullis.getWidth(),
                        portcullis.getHeight(), BlockFace.SOUTH, portcullis.getType());
            default:
                return portcullis;
        }
    }

    /**
     * Scans for a valid portcullis structure in the given direction.
     * Validates width, height, and frame integrity.
     */
    private Portcullis findPortcullisInDirection(final Block block, final BlockFace direction) {
        final Block powerBlock = block.getRelative(direction);
        final Material powerBlockType = powerBlock.getBlockData().getMaterial();

        if (!isPotentialPowerBlock(powerBlockType)) {
            TraceLogger.step("Detection", "Power block type not allowed", TraceLogger.TraceLevel.BASIC);
            TraceLogger.value("Detection", "All power blocks allowed", plugin.isAllPowerBlocksAllowed(),
                    TraceLogger.TraceLevel.DEBUG);
            return null;
        }

        final Block firstPortcullisBlock = powerBlock.getRelative(direction);
        TraceLogger.block("Detection", "First portcullis candidate", firstPortcullisBlock,
                TraceLogger.TraceLevel.DEBUG);
        TraceLogger.value("Detection", "First portcullis block type", firstPortcullisBlock.getType(),
                TraceLogger.TraceLevel.DEBUG);
        TraceLogger.value("Detection", "Is potential portcullis block",
                isPotentialPortcullisBlock(firstPortcullisBlock), TraceLogger.TraceLevel.DEBUG);
        TraceLogger.value("Detection", "Allowed portcullis materials", plugin.getPortcullisMaterials(),
                TraceLogger.TraceLevel.DEBUG);
        // if (!isPotentialPortcullisBlock(firstPortcullisBlock)) {
        // TraceLogger.step("Detection", "First block not recognized as portcullis",
        // TraceLogger.TraceLevel.BASIC);
        // return null;
        // }

        final Material portcullisType = firstPortcullisBlock.getBlockData().getMaterial();
        if (portcullisType == powerBlockType)
            return null;

        Block lastPortcullisBlock = firstPortcullisBlock.getRelative(direction);
        if (!isPortcullisBlock(portcullisType, lastPortcullisBlock))
            return null;

        int width = 2;
        Block nextBlock = lastPortcullisBlock.getRelative(direction);
        while (isPortcullisBlock(portcullisType, nextBlock)) {
            width++;
            lastPortcullisBlock = nextBlock;
            nextBlock = lastPortcullisBlock.getRelative(direction);
        }

        int highestY = firstPortcullisBlock.getY();
        Block nextBlockUp = firstPortcullisBlock.getRelative(BlockFace.UP);
        while (isPortcullisBlock(portcullisType, nextBlockUp)) {
            highestY++;
            nextBlockUp = nextBlockUp.getRelative(BlockFace.UP);
        }

        int lowestY = firstPortcullisBlock.getY();
        Block nextBlockDown = firstPortcullisBlock.getRelative(BlockFace.DOWN);
        while (isPortcullisBlock(portcullisType, nextBlockDown)) {
            lowestY--;
            nextBlockDown = nextBlockDown.getRelative(BlockFace.DOWN);
        }

        final int height = highestY - lowestY + 1;
        if (height < 2)
            return null;

        final int x = firstPortcullisBlock.getX();
        final int y = lowestY;
        final int z = firstPortcullisBlock.getZ();
        final World world = firstPortcullisBlock.getWorld();

        // Validate frame integrity
        for (int i = -1; i <= width; i++) {
            for (int dy = -1; dy <= height; dy++) {
                int dx = i * direction.getModX();
                int dz = i * direction.getModZ();
                Block current = world.getBlockAt(x + dx, y + dy, z + dz);

                boolean isFrame = ((i == -1 || i == width) && dy != -1 && dy != height)
                        || ((dy == -1 || dy == height) && i != -1 && i != width);

                if (isFrame && !current.getType().isSolid() && !plugin.isAllowFloating()) {
                    TraceLogger.step("FrameCheck",
                            "Frame block not solid and floating not allowed: " + current.getType(),
                            TraceLogger.TraceLevel.BASIC);
                    return null;
                }

                boolean isInside = (i >= 0 && i < width) && (dy >= 0 && dy < height);

                if (isInside && !isPortcullisBlock(portcullisType, current)) {
                    TraceLogger.step("FrameCheck", "Gate block mismatch at " + current.getLocation(),
                            TraceLogger.TraceLevel.BASIC);
                    return null;
                }

                TraceLogger.block("FrameCheck", "Frame block", current, TraceLogger.TraceLevel.DEBUG);
                TraceLogger.value("FrameCheck", "Is frame", isFrame, TraceLogger.TraceLevel.DEBUG);
                TraceLogger.value("FrameCheck", "Is portcullis block", isPortcullisBlock(portcullisType, current),
                        TraceLogger.TraceLevel.DEBUG);
            }
        }
        TraceLogger.block("Detection", "Power block", powerBlock, TraceLogger.TraceLevel.DEBUG);
        TraceLogger.value("Detection", "Power block type", powerBlockType, TraceLogger.TraceLevel.DEBUG);
        TraceLogger.block("Detection", "First gate block", firstPortcullisBlock, TraceLogger.TraceLevel.DEBUG);
        TraceLogger.value("Detection", "First gate block type", firstPortcullisBlock.getType(),
                TraceLogger.TraceLevel.DEBUG);
        TraceLogger.value("Detection", "Gate width", width, TraceLogger.TraceLevel.DEBUG);
        TraceLogger.value("Detection", "Gate height", height, TraceLogger.TraceLevel.DEBUG);
        TraceLogger.step("Detection", "Portcullis structure validated successfully", TraceLogger.TraceLevel.BASIC);
        return new Portcullis(world.getName(), x, z, y, width, height, direction, portcullisType);
    }

    /**
     * Determines if a block type is allowed as a power source.
     */
    private boolean isPotentialPowerBlock(final Material material) {
        TraceLogger.value("Detection", "Checking power block type", material, TraceLogger.TraceLevel.DEBUG);
        return plugin.isAllPowerBlocksAllowed()
                ? MaterialGroups.POWER_RELATED.contains(material)
                : plugin.getPowerRelatedMaterials().contains(material);

    }

    /**
     * Determines if a block is a valid portcullis material.
     */
    private boolean isPotentialPortcullisBlock(final Block block) {
        TraceLogger.value("Detection", "Checking portcullis block", block.getType(), TraceLogger.TraceLevel.DEBUG);
        return plugin.getPortcullisMaterials().contains(block.getBlockData().getMaterial());
    }

    /**
     * Checks if a block matches the expected portcullis type.
     */
    private boolean isPortcullisBlock(final Material portcullisType, final Block block) {
        return block.getBlockData().getMaterial() == portcullisType;
    }
}