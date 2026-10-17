package com.alan.clients.module.impl.other;

import com.alan.clients.util.mc.MCHooks;
import com.alan.clients.component.impl.player.TargetComponent;
import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.motion.PostMotionEvent;
import com.alan.clients.module.Module;
import com.alan.clients.module.api.Category;
import com.alan.clients.module.api.ModuleInfo;
import com.alan.clients.util.chat.ChatUtil;
import com.alan.clients.util.math.MathUtil;
import com.alan.clients.util.player.MoveUtil;
import com.alan.clients.util.rotation.RotationUtil;
import com.alan.clients.util.vector.Vector2f;
import com.alan.clients.util.vector.Vector3d;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import com.alan.clients.util.time.StopWatch;

import java.io.File;
import java.io.FileWriter;
import java.util.Map;

@ModuleInfo(aliases = {"Data"}, description = "module.other.irc.description", category = Category.PLAYER)
public final class Data extends Module {

    private File data;
    private FileWriter fileWriter;
    private StopWatch start = new StopWatch();
    private Entity target;

    @Override
    public void onEnable() {
        try {
            data = new File("C:\\Users\\alanw\\OneDrive\\Desktop\\data.txt");
            data.delete();
            data.createNewFile();

            fileWriter = new FileWriter("C:\\Users\\alanw\\OneDrive\\Desktop\\data.txt");
            ChatUtil.display("Started writing to file");
        } catch (Exception exception) {
            exception.printStackTrace();
            ChatUtil.display("Failed To Write File");
        }

        start.reset();
    }

    @Override
    public void onDisable() {
        try {
            fileWriter.close();
            ChatUtil.display("Finished writing to file");
        } catch (Exception exception) {
            exception.printStackTrace();
            ChatUtil.display("Failed to write file");
        }
    }

    private Vector3d targetPosition = new Vector3d(0, 0, 0);
    private Vector3d playerPosition = new Vector3d(0, 0, 0);
    private Vector2f perfectRotations = new Vector2f(0, 0);
    private Vector2f previousRotations = new Vector2f(0, 0);
    private Vector2f previousTargetRotations = new Vector2f(0, 0);
    private double playerOffsetDistance;

    @EventLink
    public final Listener<PostMotionEvent> onPostMotion = event -> {
        target = TargetComponent.getTarget(16);
        if (target == null) {
            start.reset();
            return;
        }

        Entity player = mc.thePlayer;
        JsonObject json = new JsonObject();

        Vector2f playerPerfect = getPerfectAngles(target);
        Vector2f playerOffset = new Vector2f((float) MathHelper.wrapAngleTo180_float(playerPerfect.getX() - mc.thePlayer.rotationYaw), playerPerfect.getY() - mc.thePlayer.rotationPitch);
        json.add("playerOffsetYaw", new com.google.gson.JsonParser().parse(Double.toString(playerOffset.getX())));
        json.add("playerOffsetPitch", new com.google.gson.JsonParser().parse(Double.toString(playerOffset.getY())));

        Vector2f targetPerfect = getPerfectAngles(target, player);
        Vector2f targetOffset = new Vector2f((float) MathHelper.wrapAngleTo180_float(targetPerfect.getX() - target.rotationYaw), targetPerfect.getY() - target.rotationPitch);

        json.add("targetOffsetYaw", new com.google.gson.JsonParser().parse(Double.toString(targetOffset.getX())));
        json.add("targetOffsetPitch", new com.google.gson.JsonParser().parse(Double.toString(targetOffset.getY())));

        double distance = player.getDistanceToEntity(target);
        json.add("distance", new com.google.gson.JsonParser().parse(Double.toString(distance)));

        double playerDeltaHorizontalDistance = MathUtil.getDistance(player.prevPosX, player.posY, player.prevPosZ, target.posX, player.posY, target.posZ) - MathUtil.getDistance(player.posX, player.posY, player.posZ, target.posX, player.posY, target.posZ);
        json.add("playerDeltaHorizontalDistance", new com.google.gson.JsonParser().parse(Double.toString(playerDeltaHorizontalDistance)));

        System.out.println("Data " + playerDeltaHorizontalDistance);

        double playerDeltaVerticalDistance = (player.prevPosY - target.posY) - (player.posY - target.posY);
        json.add("playerDeltaVerticalDistance", new com.google.gson.JsonParser().parse(Double.toString(playerDeltaVerticalDistance)));

        double enemyDeltaHorizontalDistance = MathUtil.getDistance(targetPosition.getX(), target.posY, targetPosition.getZ(), player.posX, target.posY, player.posZ) - MathUtil.getDistance(target.posX, target.posY, target.posZ, player.posX, target.posY, player.posZ);
        json.add("enemyDeltaHorizontalDistance", new com.google.gson.JsonParser().parse(Double.toString(enemyDeltaHorizontalDistance)));

        double enemyDeltaVerticalDistance = (targetPosition.getY() - player.posY) - (target.posY - player.posY);
        json.add("enemyDeltaVerticalDistance", new com.google.gson.JsonParser().parse(Double.toString(enemyDeltaVerticalDistance)));

        double enemyOffsetDistance = Math.sqrt(Math.pow(enemyDeltaHorizontalDistance, 2) + Math.pow(enemyDeltaVerticalDistance, 2));
        json.add("enemyOffsetDistance", new com.google.gson.JsonParser().parse(Double.toString(enemyOffsetDistance)));

        double twoDimensionalAngleToTarget = MathHelper.wrapAngleTo180_double(Math.toDegrees(Math.atan2(playerOffset.getX(), playerOffset.getY())));
        json.add("twoDimensionalAngleToTarget", new com.google.gson.JsonParser().parse(Double.toString(twoDimensionalAngleToTarget)));

        double xDistance = target.posX - player.posX;

        double yDistance = target.posY - player.posY;
        json.add("distancey", new com.google.gson.JsonParser().parse(Double.toString(yDistance)));

        double zDistance = target.posZ - player.posZ;

        double xPlayerDisplacement = player.posX - playerPosition.getX();
        double yPlayerDisplacement = player.posY - playerPosition.getY();
        double zPlayerDisplacement = player.posZ - playerPosition.getZ();

        double speedPlayer = Math.sqrt(xPlayerDisplacement * xPlayerDisplacement + yPlayerDisplacement * yPlayerDisplacement + zPlayerDisplacement * zPlayerDisplacement);

        double xTargetDisplacement = target.posX - targetPosition.getX();
        double yTargetDisplacement = target.posY - targetPosition.getY();
        double zTargetDisplacement = target.posZ - targetPosition.getZ();

        double speedTarget = Math.sqrt(xTargetDisplacement * xTargetDisplacement + yTargetDisplacement * yTargetDisplacement + zTargetDisplacement * zTargetDisplacement);

        double deltaHorizontalAngle = RotationUtil.move(getPerfectAngles(target), new Vector2f(perfectRotations.getX(), 0), 999999).getX();
        deltaHorizontalAngle = Double.isNaN(deltaHorizontalAngle) ? 0 : deltaHorizontalAngle;
        json.add("deltaHorizontalAngle", new com.google.gson.JsonParser().parse(Double.toString(deltaHorizontalAngle)));

        double deltaVerticalAngle = getPerfectAngles(target).getY() - perfectRotations.getY();
        json.add("deltaVerticalAngle", new com.google.gson.JsonParser().parse(Double.toString(deltaVerticalAngle)));

        double horizontalMovementAnglePlayer = MathHelper.wrapAngleTo180_float((float) (360 - Math.atan2(xPlayerDisplacement, zPlayerDisplacement) * (180 / Math.PI) - 180)) + 180;

        double verticalMovementAnglePlayer = Math.atan2(yTargetDisplacement, MoveUtil.speed()) * (180 / Math.PI);

        double targetMovementAngle = MathHelper.wrapAngleTo180_double(360 - Math.atan2(xTargetDisplacement, zTargetDisplacement) * (180 / Math.PI) - 180) + 180;
        double playerYawOffsetFromMovementAngle = MathHelper.wrapAngleTo180_double(player.rotationYaw - horizontalMovementAnglePlayer);
        double targetYawOffsetFromMovementAngle = MathHelper.wrapAngleTo180_double(target.rotationYaw - targetMovementAngle);
        double playerMovementOffsetFromRight = Math.abs(playerYawOffsetFromMovementAngle) > 90 ? 90 - Math.abs(playerYawOffsetFromMovementAngle) % 90 : Math.abs(playerYawOffsetFromMovementAngle) % 90;
        double playerForwardPercentage = (1 - MathHelper.clamp_double(playerMovementOffsetFromRight / 90, 0, 1)) * (Math.abs(playerYawOffsetFromMovementAngle) > 90 ? -1 : 1);
        double forwardVelocityPlayer = MoveUtil.speed() * playerForwardPercentage;
        double lateralVelocityPlayer = MoveUtil.speed() * (1 - Math.abs(playerForwardPercentage)) * (playerYawOffsetFromMovementAngle < 0 ? -1 : 1);

        json.add("forwardVelocityPlayer", new com.google.gson.JsonParser().parse(Double.toString(forwardVelocityPlayer)));
        json.add("lateralVelocityPlayer", new com.google.gson.JsonParser().parse(Double.toString(lateralVelocityPlayer)));

        json.add("verticalVelocityPlayer", new com.google.gson.JsonParser().parse(Double.toString(yPlayerDisplacement)));
        json.add("verticalVelocityTarget", new com.google.gson.JsonParser().parse(Double.toString(yTargetDisplacement)));

        double euclideanTargetSpeed = Math.sqrt(xTargetDisplacement * xTargetDisplacement + zTargetDisplacement * zTargetDisplacement);
        double targetMovementOffsetFromRight = Math.abs(targetYawOffsetFromMovementAngle) > 90 ? 90 - Math.abs(targetYawOffsetFromMovementAngle) % 90 : Math.abs(targetYawOffsetFromMovementAngle) % 90;
        double targetForwardPercentage = (1 - MathHelper.clamp_double(targetMovementOffsetFromRight / 90, 0, 1)) * (Math.abs(targetYawOffsetFromMovementAngle) > 90 ? -1 : 1);

        double forwardVelocityTarget = euclideanTargetSpeed * targetForwardPercentage;
        double lateralVelocityTarget = euclideanTargetSpeed * (1 - Math.abs(targetForwardPercentage)) * (targetYawOffsetFromMovementAngle < 0 ? -1 : 1);

        json.add("forwardVelocityTarget", new com.google.gson.JsonParser().parse(Double.toString(forwardVelocityTarget)));
        json.add("lateralVelocityTarget", new com.google.gson.JsonParser().parse(Double.toString(lateralVelocityTarget)));

        double playerDeltaHorizontalMovementAngle = MathHelper.wrapAngleTo180_double((RotationUtil.calculate(new Vector3d(player.posX, player.posY, player.posZ),
                new Vector3d(target.posX, player.posY, target.posZ)).getX()) - (RotationUtil.calculate(new Vector3d(player.prevPosX, player.posY, player.prevPosZ), new Vector3d(target.posX, player.posY, target.posZ)).getX()));
        json.add("playerDeltaHorizontalMovementAngle", new com.google.gson.JsonParser().parse(Double.toString(playerDeltaHorizontalMovementAngle)));

        double playerDeltaVerticalMovementAngle = RotationUtil.calculate(new Vector3d(player.posX, player.posY, player.posZ), new Vector3d(target.posX, target.posY, target.posZ)).getY() - RotationUtil.calculate(new Vector3d(player.posX, player.prevPosY, player.posZ), new Vector3d(target.posX, target.posY, target.posZ)).getY();
        json.add("playerDeltaVerticalMovementAngle", new com.google.gson.JsonParser().parse(Double.toString(playerDeltaVerticalMovementAngle)));

        double targetDeltaHorizontalMovementAngle = MathHelper.wrapAngleTo180_double((RotationUtil.calculate(new Vector3d(target.posX, target.posY, target.posZ),
                new Vector3d(player.posX, target.posY, player.posZ)).getX()) - (RotationUtil.calculate(new Vector3d(targetPosition.getX(), target.posY, targetPosition.getZ()), new Vector3d(player.posX, target.posY, player.posZ)).getX()));
        json.add("targetDeltaHorizontalMovementAngle", new com.google.gson.JsonParser().parse(Double.toString(targetDeltaHorizontalMovementAngle)));

        double targetDeltaVerticalMovementAngle = RotationUtil.calculate(new Vector3d(player.posX, player.posY, player.posZ), new Vector3d(target.posX, target.posY, target.posZ)).getY() - RotationUtil.calculate(new Vector3d(player.posX, player.posY, player.posZ), new Vector3d(target.posX, targetPosition.getY(), target.posZ)).getY();
        json.add("targetDeltaVerticalMovementAngle", new com.google.gson.JsonParser().parse(Double.toString(targetDeltaVerticalMovementAngle)));

        double deltaPlayerOffsetDistance = playerOffsetDistance - Math.sqrt(Math.pow(playerOffset.getX(), 2) + Math.pow(playerOffset.getY(), 2));
        json.add("deltaPlayerOffsetDistance", new com.google.gson.JsonParser().parse(Double.toString(deltaPlayerOffsetDistance)));

        playerOffsetDistance = Math.sqrt(Math.pow(playerOffset.getX(), 2) + Math.pow(playerOffset.getY(), 2));
        json.add("playerOffsetDistance", new com.google.gson.JsonParser().parse(Double.toString(playerOffsetDistance)));

        double deltaRotation = Math.sqrt(Math.pow((player.rotationYaw - previousRotations.getX()), 2) + Math.pow((player.rotationPitch - previousRotations.getY()), 2));
        json.add("deltaRotation", new com.google.gson.JsonParser().parse(Double.toString(deltaRotation)));

        double targetDeltaRotation = Math.sqrt(Math.pow((target.rotationYaw - previousTargetRotations.getX()), 2) + Math.pow((target.rotationPitch - previousTargetRotations.getY()), 2));
        json.add("targetDeltaRotation", new com.google.gson.JsonParser().parse(Double.toString(targetDeltaRotation)));

        double playerDeltaYaw = MathHelper.wrapAngleTo180_float(previousRotations.getX() - player.rotationYaw);
        json.add("playerDeltaYaw", new com.google.gson.JsonParser().parse(Double.toString(playerDeltaYaw)));

        double playerDeltaPitch = previousRotations.getY() - player.rotationPitch;
        json.add("playerDeltaPitch", new com.google.gson.JsonParser().parse(Double.toString(playerDeltaPitch)));

        double targetDeltaYaw = MathHelper.wrapAngleTo180_float(previousTargetRotations.getX() - target.rotationYaw);
        json.add("targetDeltaYaw", new com.google.gson.JsonParser().parse(Double.toString(targetDeltaYaw)));

        double targetDeltaPitch = previousTargetRotations.getY() - target.rotationPitch;
        json.add("targetDeltaPitch", new com.google.gson.JsonParser().parse(Double.toString(targetDeltaPitch)));

        perfectRotations = getPerfectAngles(target);
        playerPosition = new Vector3d(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        targetPosition = new Vector3d(target.posX, target.posY, target.posZ);

        Vector2f reconstitutedRotations = new Vector2f(MathHelper.wrapAngleTo180_float(playerPerfect.getX() - playerOffset.getX()), playerPerfect.getY() - playerOffset.getY());
        Vector2f reconstitutedDeltaRotations = new Vector2f(MathHelper.wrapAngleTo180_float(reconstitutedRotations.getX() - previousRotations.getX()), reconstitutedRotations.getY() - previousRotations.getY());

        Vector2f deltaRotations = new Vector2f(MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - previousRotations.getX()), mc.thePlayer.rotationPitch - previousRotations.getY());

        if (start.finished(1000)) {
            for (Map.Entry<String, JsonElement> element : json.entrySet()) {
                fileWriter.write(element.getValue().getAsString() + "\t");
            }

            fileWriter.write("\n");
        }

        previousRotations = new Vector2f(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        previousTargetRotations = new Vector2f(target.rotationYaw, target.rotationPitch);
    };

    public Vector2f getPerfectAngles(Entity target) {
        return getPerfectAngles(mc.thePlayer, target);
    }

    public Vector2f getPerfectAngles(Entity player, Entity target) {
        Vector2f rotations = RotationUtil.calculate(MCHooks.getCustomPositionVector(player).add(0, player.getEyeHeight(), 0),
                MCHooks.getCustomPositionVector(target).add(0, mc.thePlayer.getEyeHeight(), 0));
        if (rotations.y == 0) rotations.y = Math.abs(rotations.getY());
        return rotations;
    }
}
