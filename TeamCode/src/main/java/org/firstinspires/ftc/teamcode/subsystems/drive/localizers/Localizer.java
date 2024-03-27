package org.firstinspires.ftc.teamcode.subsystems.drive.localizers;

import static org.firstinspires.ftc.teamcode.utils.Globals.GET_LOOP_TIME;

import android.util.Log;

import com.acmerobotics.dashboard.canvas.Canvas;
import com.acmerobotics.dashboard.config.Config;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.teamcode.sensors.Sensors;
import org.firstinspires.ftc.teamcode.subsystems.drive.Drivetrain;
import org.firstinspires.ftc.teamcode.utils.DashboardUtil;
import org.firstinspires.ftc.teamcode.utils.Encoder;
import org.firstinspires.ftc.teamcode.utils.Globals;
import org.firstinspires.ftc.teamcode.utils.MovingAverage;
import org.firstinspires.ftc.teamcode.utils.Pose2d;
import org.firstinspires.ftc.teamcode.utils.TelemetryUtil;
import org.firstinspires.ftc.teamcode.utils.Utils;
import org.firstinspires.ftc.teamcode.vision.Vision;
import org.firstinspires.ftc.teamcode.vision.apriltags.AprilTagLocalizer;

import java.util.ArrayList;

@Config
// TODO: Get to cookin in teleop -- Eric
public class Localizer {
    Sensors sensors;
    Drivetrain drivetrain;

    public Encoder[] encoders;
    long lastTime = System.nanoTime();

    public double x = 0;
    public double y = 0;
    public double heading = 0;

    double odoHeading = 0;

    public Pose2d currentPose = new Pose2d(0,0,0);
    public Pose2d currentVel = new Pose2d(0,0,0);
    public Pose2d relCurrentVel = new Pose2d(0,0,0);
    public Pose2d currentAcc = new Pose2d(0,0,0);
    public Pose2d relCurrentAcc = new Pose2d(0,0,0);
    public Pose2d currentPowerVector = new Pose2d(0,0,0);

    ArrayList<Pose2d> poseHistory = new ArrayList<Pose2d>();
    ArrayList<Pose2d> velPoseHistory = new ArrayList<>();
    ArrayList<Pose2d> relVelPoseHistory = new ArrayList<>();
    ArrayList<Pose2d> relHistory = new ArrayList<Pose2d>();
    ArrayList<Long> nanoTimes = new ArrayList<Long>();

    public boolean useAprilTag;

    AprilTagLocalizer aprilTagLocalizer;
    double minAprilTagWeight = 1/40;
    double maxVel = 0.0;


    public Localizer(HardwareMap hardwareMap, Sensors sensors, boolean useAprilTag, boolean useIMU, Vision vision, Drivetrain drivetrain) {
        this.sensors = sensors;
        this.useAprilTag = useAprilTag;
        this.drivetrain = drivetrain;

        encoders = new Encoder[3];

        encoders[0] = new Encoder(new Pose2d(0,4.7430916033 * 0.9906939116 * 0.9973829581 * 1.00335257996+0.3552354026770107/2),  -1); // left
        encoders[1] = new Encoder(new Pose2d(0,-5.09234035968 * 0.9906939116 * 0.9973829581 * 1.00335257996+0.3552354026770107/2),1); // right
        encoders[2] = new Encoder(new Pose2d(-6.35+0.361015534989162/2, 0),  -1); // back (7.1660442092285175)

//        encoders[0] = new Encoder(new Pose2d(0,4.811892926264648),  -1); // left
//        encoders[1] = new Encoder(new Pose2d(0,-5.02669342987793),1); // right
//        encoders[2] = new Encoder(new Pose2d(-6.022134808388673, 0),  -1); // back (7.1660442092285175)

        this.useAprilTag = useAprilTag;
        this.sensors.useIMU = useIMU;

        if (useAprilTag) {
            aprilTagLocalizer = new AprilTagLocalizer(vision, this);
        }
    }

    public void updateEncoders(int[] encoders) {
        for (int i = 0; i < this.encoders.length; i ++){
            this.encoders[i].update(encoders[i]);
        }
    }
    double headingOffset = 0;
    public void setPose(double x, double y, double h) {
        this.x = x;
        this.y = y;
        headingOffset += h - this.heading;
        currentPose = new Pose2d(x, y, h);
    }

    public Pose2d getPoseEstimate() {
        return new Pose2d(currentPose.x, currentPose.y, currentPose.heading);
    }


    public void setPoseEstimate(Pose2d pose2d) {
        setPose(pose2d.getX(), pose2d.getY(), pose2d.getHeading());
        Globals.START_HEADING_OFFSET = pose2d.getHeading();
    }

    public Pose2d getRelativePoseVelocity() {
        return new Pose2d(relCurrentVel.x, relCurrentVel.y, relCurrentVel.heading);
    }

    double weight;
    double aprilTagHeadingMerge = 0;

    public void update() {
        // TODO: Remove calculation for TeleOp -- Eric
        long currentTime = System.nanoTime();
        double loopTime = (double)(currentTime-lastTime)/1.0E9;
        lastTime = currentTime;

        double deltaLeft = encoders[0].getDelta();
        double deltaRight = encoders[1].getDelta();
        double deltaBack = encoders[2].getDelta();
        double leftY = encoders[0].y;
        double rightY = encoders[1].y;
        double backX = encoders[2].x;

        //This is the heading because the heading is proportional to the difference between the left and right wheel.
        double deltaHeading = (deltaRight - deltaLeft)/(leftY-rightY);
        //This gives us deltaY because the back minus theta*R is the amount moved to the left minus the amount of movement in the back encoder due to change in heading
        double relDeltaY = deltaBack - deltaHeading*backX;
        //This is a weighted average for the amount moved forward with the weights being how far away the other one is from the center
        double relDeltaX = (deltaRight*leftY - deltaLeft*rightY)/(leftY-rightY);

        relHistory.add(0,new Pose2d(relDeltaX,relDeltaY,deltaHeading));

        if (Math.abs(deltaHeading) > 0.01) { // this avoids the issue where deltaHeading = 0 and then it goes to undefined. This effectively does L'Hopital's
            double r1 = relDeltaX / deltaHeading;
            double r2 = relDeltaY / deltaHeading;
            relDeltaX = Math.sin(deltaHeading) * r1 - (1.0 - Math.cos(deltaHeading)) * r2;
            relDeltaY = (1.0 - Math.cos(deltaHeading)) * r1 + Math.sin(deltaHeading) * r2;
        }
        x += relDeltaX * Math.cos(heading) - relDeltaY * Math.sin(heading);
        y += relDeltaY * Math.cos(heading) + relDeltaX * Math.sin(heading);

        odoHeading = (encoders[1].getCurrentDist()-encoders[0].getCurrentDist())/(leftY-rightY);

        if (this.sensors.useIMU) {
            updateHeadingWithIMU(sensors.getImuHeading());
        }

        aprilTagHeadingMerge = 0;
        if (useAprilTag && nanoTimes.size() > 5) {
            Pose2d aprilTagPose = aprilTagLocalizer.update(); // update april tags

            if (aprilTagPose != null && !aprilTagPose.isNaN()) {
                Pose2d errorBetweenInterpolatedPastPoseAndAprilTag = new Pose2d(
                        aprilTagPose.x - interpolatedPastPose.x,
                        aprilTagPose.y - interpolatedPastPose.y,
                        Utils.headingClip(aprilTagPose.heading - interpolatedPastPose.heading - headingDif)
                );

                maxVel = Math.sqrt(Math.pow(relCurrentVel.x,2) + Math.pow(relCurrentVel.y,2));
                // TODO: Tune weights
                weight = Math.max(1/Math.max(maxVel,8), minAprilTagWeight); // as speed increases we should decrease weight of april tags
//                weight/=5;

                // resetting odo with april tag data
                Pose2d changeInPosition = new Pose2d(0,0,0);
                if (maxVel < 25) {
                    changeInPosition.x = errorBetweenInterpolatedPastPoseAndAprilTag.x * weight;
                    changeInPosition.y = errorBetweenInterpolatedPastPoseAndAprilTag.y * weight;
                }
                if (maxVel < 3 && Math.abs(relCurrentVel.heading) < Math.toRadians(30)) {
                    changeInPosition.heading = errorBetweenInterpolatedPastPoseAndAprilTag.heading * weight;
                }
                for (Pose2d pose : poseHistory){
                    pose.add(changeInPosition);
                }
                x += changeInPosition.x;
                y += changeInPosition.y;
                aprilTagHeadingMerge = changeInPosition.heading;
            }
        }

//        mergeUltrasonics();

        heading = odoHeading + imuMerge + headingOffset /*+ aprilTagHeadingMerge*/ + Globals.START_HEADING_OFFSET;

        currentPose = new Pose2d(x, y, heading);

        nanoTimes.add(0, System.nanoTime());
        poseHistory.add(0,currentPose);

        updateVelocity();
        updateAcceleration();
        updateField();
    }

    public Pose2d interpolatedPastPose;

    public void findPastInterpolatedPose(long aprilTagPoseTime) {
        int indexOfDesiredNanoTime = 0;

        for (long time : nanoTimes) {
            if (time > aprilTagPoseTime) {
                indexOfDesiredNanoTime++;
            } else {
                break;
            }
        }

        indexOfDesiredNanoTime = Math.min(indexOfDesiredNanoTime, nanoTimes.size()-1);

        Pose2d pastTimeRobotPose = poseHistory.get(indexOfDesiredNanoTime).clone();
        Pose2d pastTimeRobotPose2 = poseHistory.get(Math.max(0, indexOfDesiredNanoTime-1)).clone();

        if (indexOfDesiredNanoTime != 0) {
            Pose2d errorInPastPoses = new Pose2d(
                    pastTimeRobotPose2.x - pastTimeRobotPose.x,
                    pastTimeRobotPose2.y - pastTimeRobotPose.y,
                    Utils.headingClip(pastTimeRobotPose2.heading - pastTimeRobotPose.heading)
            );

            double timeWeight = (double) (aprilTagPoseTime - nanoTimes.get(indexOfDesiredNanoTime)) /
                    (double) (nanoTimes.get(Math.max(0, indexOfDesiredNanoTime - 1)) - nanoTimes.get(indexOfDesiredNanoTime));

            interpolatedPastPose = new Pose2d(
                    pastTimeRobotPose.x + errorInPastPoses.x * timeWeight,
                    pastTimeRobotPose.y + errorInPastPoses.y * timeWeight,
                    pastTimeRobotPose.heading + errorInPastPoses.heading * timeWeight
            );
        } else {
            interpolatedPastPose = pastTimeRobotPose;
        }
    }

    double headingDif = 0.0;
    boolean firstLoop = true;
    double lastImuHeading;
    double lastOdoHeading;

    double imuMerge = 0;

    public void updateHeadingWithIMU(double imuHeading) {
        if (firstLoop){
            lastOdoHeading = odoHeading;
            lastImuHeading = lastOdoHeading;
            firstLoop = false;
        }
        if (sensors.imuJustUpdated) {
            headingDif += (imuHeading-lastImuHeading) - (odoHeading-lastOdoHeading);// This is error for heading from IMU
            headingDif = Utils.headingClip(headingDif);
            lastImuHeading = imuHeading;
            lastOdoHeading = odoHeading;
        }
        double percentHeadingDif = (sensors.timeTillNextIMUUpdate/1.0e3)/GET_LOOP_TIME();
        if (percentHeadingDif > 1){
            percentHeadingDif = 1;
        }
        else if (percentHeadingDif <= 0){
            percentHeadingDif = 1;
        }
        double headingErrAdd = headingDif * (1/percentHeadingDif);

        headingDif -= headingErrAdd;
        imuMerge += headingErrAdd;
    }

    double leftDist = 0.0;
    double rightDist = 0.0;

    double leftXOffset = 5.5;
    double leftYOffset = 2.625;
    double rightXOffset = 5.5;
    double rightYOffset = -2.625;

    double lastLeftDist = 0.0;
    double lastRightDist = 0.0;

    public void mergeUltrasonics() {
        //leftDist = sensors.getDistLeft();
        //rightDist = sensors.getDistRight();

        double x_sign = Math.abs(Utils.headingClip(heading)) < Math.toRadians(90) ? 1 : -1;
        double y_sign = Math.signum(Utils.headingClip(heading));

        Pose2d relativeWallLocationLeft = new Pose2d(leftXOffset + leftDist, leftYOffset);
        Pose2d globalWallLocationLeft = new Pose2d(
                x + Math.cos(heading)*relativeWallLocationLeft.x - Math.sin(heading)*relativeWallLocationLeft.y,
                y + Math.sin(heading)*relativeWallLocationLeft.x + Math.cos(heading)*relativeWallLocationLeft.y
        );

        Pose2d relativeWallLocationRight = new Pose2d(rightXOffset + rightDist, rightYOffset);
        Pose2d globalWallLocationRight = new Pose2d(
                x + Math.cos(heading)*relativeWallLocationRight.x - Math.sin(heading)*relativeWallLocationRight.y,
                y + Math.sin(heading)*relativeWallLocationRight.x + Math.cos(heading)*relativeWallLocationRight.y
        );

        if (Globals.mergeUltrasonics && leftDist != lastLeftDist && rightDist != lastRightDist) { // actually merging localization
            if (Math.abs(globalWallLocationLeft.x - 70.5 * x_sign) < 4) {
                x += (70.5 * x_sign - globalWallLocationLeft.x) * 0.1;
            }
            if (Math.abs(globalWallLocationLeft.y - 70.5 * y_sign) < 4) {
                y += (70.5 * y_sign - globalWallLocationLeft.y) * 0.1;
            }

            if (Math.abs(globalWallLocationRight.x - 70.5 * x_sign) < 4) {
                x += (70.5 * x_sign - globalWallLocationRight.x) * 0.1;
            }
            if (Math.abs(globalWallLocationRight.y - 70.5 * y_sign) < 4) {
                y += (70.5 * y_sign - globalWallLocationRight.y) * 0.1;
            }
        }

        lastLeftDist = leftDist;
        lastRightDist = rightDist;

        TelemetryUtil.packet.fieldOverlay().setStroke("green");
        TelemetryUtil.packet.fieldOverlay().strokeCircle(globalWallLocationLeft.x, globalWallLocationLeft.y, 1);

        TelemetryUtil.packet.fieldOverlay().setStroke("blue");
        TelemetryUtil.packet.fieldOverlay().strokeCircle(globalWallLocationRight.x, globalWallLocationRight.y, 1);
    }

    public void updatePowerVector(double[] p){
        for (int i = 0; i < p.length; i ++){
            p[i] = Math.max(Math.min(p[i],1),-1);
        }
        double forward = (p[0] + p[1] + p[2] + p[3]) / 4;
        double left = (-p[0] + p[1] - p[2] + p[3]) / 4; //left power is less than 1 of forward power
        double turn = (-p[0] - p[1] + p[2] + p[3]) / 4;
        currentPowerVector.x = forward * Math.cos(heading) - left * Math.sin(heading);
        currentPowerVector.y = left * Math.cos(heading) + forward * Math.sin(heading);
        currentPowerVector.heading = turn;
    }

    public void updateVelocity() {
        double targetVelTimeEstimate = 0.35;
        double actualVelTime = 0;
        double relDeltaXTotal = 0;
        double relDeltaYTotal = 0;
        double totalTime = 0;
        int lastIndex = 0;
        long start = nanoTimes.size() != 0 ? nanoTimes.get(0) : 0;
        for (int i = 0; i < nanoTimes.size(); i++){
            totalTime = (double)(start - nanoTimes.get(i)) / 1.0E9;
            if (totalTime <= targetVelTimeEstimate){
                actualVelTime = totalTime;
                relDeltaXTotal += relHistory.get(i).getX();
                relDeltaYTotal += relHistory.get(i).getY();
                lastIndex = i;
            }
        }
        if (actualVelTime != 0) {
            currentVel = new Pose2d(
                    (poseHistory.get(0).getX() - poseHistory.get(lastIndex).getX()) / actualVelTime,
                    (poseHistory.get(0).getY() - poseHistory.get(lastIndex).getY()) / actualVelTime,
                    (poseHistory.get(0).getHeading() - poseHistory.get(lastIndex).getHeading()) / actualVelTime
            );
            relCurrentVel = new Pose2d(
                    (relDeltaXTotal) / actualVelTime,
                    (relDeltaYTotal) / actualVelTime,
                    (poseHistory.get(0).getHeading() - poseHistory.get(lastIndex).getHeading()) / actualVelTime
            );
        }
        else {
            currentVel = new Pose2d(0, 0, 0);
            relCurrentVel = new Pose2d(0, 0, 0);
        }
        velPoseHistory.add(0, currentVel);
        relVelPoseHistory.add(0, relCurrentVel);
        while (lastIndex + 1 < nanoTimes.size()){
            nanoTimes.remove(nanoTimes.size() - 1);
            relHistory.remove(relHistory.size() - 1);
            poseHistory.remove(poseHistory.size() - 1);
        }
    }

    public void updateAcceleration() {
        double targetAccTimeEstimate = 0.70;
        double actualAccTime = 0;
        double relDeltaXTotal = 0;
        double relDeltaYTotal = 0;
        double totalTime = 0;
        int lastIndex = 0;
        long start = nanoTimes.size() != 0 ? nanoTimes.get(0) : 0;
        for (int i = 0; i < nanoTimes.size(); i++){
            totalTime = (double)(start - nanoTimes.get(i)) / 1.0E9;
            if (totalTime <= targetAccTimeEstimate){
                actualAccTime = totalTime;
                relDeltaXTotal += relVelPoseHistory.get(i).getX();
                relDeltaYTotal += relVelPoseHistory.get(i).getY();
                lastIndex = i;
            }
        }
        if (actualAccTime != 0) {
            currentAcc = new Pose2d(
                    (velPoseHistory.get(0).getX() - velPoseHistory.get(lastIndex).getX()) / actualAccTime,
                    (velPoseHistory.get(0).getY() - velPoseHistory.get(lastIndex).getY()) / actualAccTime,
                    (velPoseHistory.get(0).getHeading() - velPoseHistory.get(lastIndex).getHeading()) / actualAccTime
            );
            relCurrentAcc = new Pose2d(
                    (relDeltaXTotal) / actualAccTime,
                    (relDeltaYTotal) / actualAccTime,
                    (velPoseHistory.get(0).getHeading() - velPoseHistory.get(lastIndex).getHeading()) / actualAccTime
            );
        }
        else {
            currentAcc = new Pose2d(0, 0, 0);
            relCurrentAcc = new Pose2d(0, 0, 0);
        }
        while (lastIndex + 1 < nanoTimes.size()){
            //nanoTimes.remove(nanoTimes.size() - 1);
            relVelPoseHistory.remove(relVelPoseHistory.size() - 1);
            velPoseHistory.remove(velPoseHistory.size() - 1);
        }
    }

    public double kalmanFilter (double value1, double value2, double value2Weight) {
        return (value1 * (1.0-value2Weight)) + (value2 * value2Weight);
    }

    MovingAverage movingAverage = new MovingAverage(100);

    public double combineHeadings(double headingError) {
        movingAverage.addData(headingError);
        double averageError = movingAverage.getMovingAverageForNum();
        movingAverage.updateValsRetroactively(averageError);
        return movingAverage.getMovingAverageForNum();
    }

    public void updateField() {
        TelemetryUtil.packet.put("x", x);
        TelemetryUtil.packet.put("y", y);
        TelemetryUtil.packet.put("heading (deg)", Math.toDegrees(heading));

        TelemetryUtil.packet.put("x speed", relCurrentVel.x);
        TelemetryUtil.packet.put("y speed", relCurrentVel.y);
        TelemetryUtil.packet.put("turn speed (deg)", Math.toDegrees(relCurrentVel.heading));

        Canvas fieldOverlay = TelemetryUtil.packet.fieldOverlay();
        DashboardUtil.drawRobot(fieldOverlay, getPoseEstimate());
    }
}