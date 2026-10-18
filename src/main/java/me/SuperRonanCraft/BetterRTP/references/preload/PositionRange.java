package me.SuperRonanCraft.BetterRTP.references.preload;

import me.SuperRonanCraft.BetterRTP.player.rtp.RTP_SHAPE;

/**
 * Is a prepared position inside the min/max radius of the RTP settings that a player is using right now?
 * (Different permission groups / custom worlds can have different radii, so a stored position is only handed out
 * when it fits the requester.)
 */
public final class PositionRange {

    private PositionRange() {
    }

    public static boolean contains(RTP_SHAPE shape, int x, int z, int centerX, int centerZ, int minRadius, int maxRadius) {
        long dx = (long) x - centerX, dz = (long) z - centerZ;
        if (shape == RTP_SHAPE.CIRCLE) {
            long d2 = dx * dx + dz * dz;
            return d2 <= (long) maxRadius * maxRadius && d2 >= (long) minRadius * minRadius;
        }
        //Square (default): outside the min radius on at least one axis, inside the max radius on both
        long ax = Math.abs(dx), az = Math.abs(dz);
        return (ax >= minRadius || az >= minRadius) && ax <= maxRadius && az <= maxRadius;
    }
}
