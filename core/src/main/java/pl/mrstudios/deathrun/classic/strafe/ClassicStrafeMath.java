package pl.mrstudios.deathrun.classic.strafe;

import org.jetbrains.annotations.NotNull;

public final class ClassicStrafeMath {

    private ClassicStrafeMath() {}

    public static @NotNull Horizontal horizontal(
            float snappedYaw,
            @NotNull ClassicStrafeService.Direction direction
    ) {
        double radians = Math.toRadians(snappedYaw);

        // Minecraft yaw 0 points south (+Z) and +90 points west (-X).
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);

        // Turning right from the snapped facing direction is yaw +90.
        double rightX = -Math.cos(radians);
        double rightZ = -Math.sin(radians);

        return switch (direction) {
            case LEFT -> new Horizontal(-rightX, -rightZ);
            case BACK -> new Horizontal(-forwardX, -forwardZ);
            case RIGHT -> new Horizontal(rightX, rightZ);
        };
    }

    public record Horizontal(double x, double z) {}
}
