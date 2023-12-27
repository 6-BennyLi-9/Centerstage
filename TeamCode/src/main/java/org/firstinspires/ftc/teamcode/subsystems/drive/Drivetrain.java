package org.firstinspires.ftc.teamcode.subsystems.drive;

import static org.firstinspires.ftc.teamcode.utils.Globals.DRIVETRAIN_ENABLED;
import static org.firstinspires.ftc.teamcode.utils.Globals.ROBOT_POSITION;
import static org.firstinspires.ftc.teamcode.utils.Globals.ROBOT_VELOCITY;

import android.util.Log;

import com.acmerobotics.dashboard.config.Config;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.configuration.typecontainers.MotorConfigurationType;

import org.firstinspires.ftc.teamcode.sensors.Sensors;
import org.firstinspires.ftc.teamcode.subsystems.drive.localizers.Localizer;
import org.firstinspires.ftc.teamcode.utils.Globals;
import org.firstinspires.ftc.teamcode.utils.PID;
import org.firstinspires.ftc.teamcode.utils.Pose2d;
import org.firstinspires.ftc.teamcode.utils.TelemetryUtil;
import org.firstinspires.ftc.teamcode.utils.Vector2;
import org.firstinspires.ftc.teamcode.utils.priority.HardwareQueue;
import org.firstinspires.ftc.teamcode.utils.priority.PriorityMotor;
import org.firstinspires.ftc.teamcode.vision.Vision;

import java.util.Arrays;
import java.util.List;

@Config
public class Drivetrain {
    public enum State {
        GO_TO_POINT,
        DRIVE,
        BRAKE,
        WAIT_AT_POINT,
        IDLE
    }
    public State state = State.IDLE;

    public PriorityMotor leftFront, leftRear, rightRear, rightFront;
    private List<PriorityMotor> motors;

    private HardwareQueue hardwareQueue;
    private Sensors sensors;

    public Localizer localizer;
    public Vision vision;

    public Drivetrain(HardwareMap hardwareMap, HardwareQueue hardwareQueue, Sensors sensors, Vision vision) {
        this.hardwareQueue = hardwareQueue;
        this.sensors = sensors;

        leftFront = new PriorityMotor(
            hardwareMap.get(DcMotorEx.class, "leftFront"),
            "leftFront",
            3, 5, sensors
        );

        leftRear = new PriorityMotor(
            hardwareMap.get(DcMotorEx.class, "leftRear"),
            "leftRear",
            3, 5, sensors
        );
        rightRear = new PriorityMotor(
            hardwareMap.get(DcMotorEx.class, "rightRear"),
            "rightRear",
            3, 5, sensors
        );
        rightFront = new PriorityMotor(
            hardwareMap.get(DcMotorEx.class, "rightFront"),
            "rightFront",
            3, 5, sensors
        );

        motors = Arrays.asList(leftFront, leftRear, rightRear, rightFront);

        for (PriorityMotor motor : motors) {
            MotorConfigurationType motorConfigurationType = motor.motor[0].getMotorType().clone();
            motorConfigurationType.setAchieveableMaxRPMFraction(1.0);
            motor.motor[0].setMotorType(motorConfigurationType);

            hardwareQueue.addDevice(motor);
        }

        setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        leftFront.motor[0].setDirection(DcMotor.Direction.REVERSE);
        leftRear.motor[0].setDirection(DcMotor.Direction.REVERSE);

        if (vision != null) {
            if (vision.tagProcessor != null) {
                localizer = new Localizer(hardwareMap, sensors,true, false, vision);
                Log.e("using vision localizer", "");
            }
        } else {
            localizer = new Localizer(hardwareMap, sensors,false, false, null);
            Log.e("NOT using vision localizer", "");
        }
        setMinPowersToOvercomeFriction();
    }

    public Drivetrain (HardwareMap hardwareMap, HardwareQueue hardwareQueue, Sensors sensors) {
        this(hardwareMap, hardwareQueue, sensors, null);
    }

    public void setMinPowersToOvercomeFriction() {
        leftFront.setMinimumPowerToOvercomeFriction(0.17493824636733907);
        leftRear.setMinimumPowerToOvercomeFriction(0.2752498640527196);
        rightRear.setMinimumPowerToOvercomeFriction(0.27855616387960147);
        rightFront.setMinimumPowerToOvercomeFriction(0.22428003736833285);
//        leftFront.setMinimumPowerToOvercomeFriction(0.44669999999 * 0.7);
//        leftRear.setMinimumPowerToOvercomeFriction(0.4696999999999 * 0.7);
//        rightRear.setMinimumPowerToOvercomeFriction(0.474699999999999 * 0.7);
//        rightFront.setMinimumPowerToOvercomeFriction(0.42039999999997 * 0.7);
    }

    public void resetMinPowersToOvercomeFriction() {
        leftFront.setMinimumPowerToOvercomeFriction(0.0);
        leftRear.setMinimumPowerToOvercomeFriction(0.0);
        rightRear.setMinimumPowerToOvercomeFriction(0.0);
        rightFront.setMinimumPowerToOvercomeFriction(0.0);
    }

    Pose2d targetPoint = new Pose2d(0,0,0);
    Pose2d lastTargetPoint = new Pose2d(0,0,0);

    double xError = 0.0;
    double yError = 0.0;
    double turnError = 0.0;

    public static double xSlowdown = 20;
    public static double ySlowdown = 20;
    public static double turnSlowdown = 60;

    public static double kAccelX = 0.0;
    public static double kAccelY = 0.0;
    public static double kAccelTurn = 0.0;

    public static double xBrakingDistanceThreshold = 5;
    public static double xBrakingSpeedThreshold = 20;
    public static double xBrakingPower = -0.22;

    public static double yBrakingDistanceThreshold = 5;
    public static double yBrakingSpeedThreshold = 16;
    public static double yBrakingPower = -0.1;

    public static double turnBrakingAngleThreshold = 20; // in degrees
    public static double turnBrakingSpeedThreshold = 135; // in degrees
    public static double turnBrakingPower = -0.15;

    double targetForwardPower = 0;
    double targetStrafePower = 0;
    double targetTurnPower = 0;

    public static PID xPID = new PID(0.085,0.0,0.01);
    public static PID yPID = new PID(0.15,0.0,0.01);
    public static PID turnPID = new PID(0.4,0.0,0.0);

    public void update() {
        if (!DRIVETRAIN_ENABLED) {
            return;
        }

        updateLocalizer();
        calculateErrors();
        updateTelemetry();

        Pose2d estimate = localizer.getPoseEstimate();
        ROBOT_POSITION = new Pose2d(estimate.x, estimate.y,estimate.heading);
        ROBOT_VELOCITY = localizer.getRelativePoseVelocity();

        switch (state) {
            case GO_TO_POINT:
                PIDF();
                break;
            case DRIVE:
                break;
            case BRAKE:
                stopAllMotors();
                state = State.WAIT_AT_POINT;
                break;
            case WAIT_AT_POINT:
                if (!atPoint()) {
                    state = State.GO_TO_POINT;
                }
                break;
            case IDLE:
                break;
        }
    }

    public void calculateErrors() {
        double deltaX = (targetPoint.x - localizer.x);
        double deltaY = (targetPoint.y - localizer.y);

        xError = Math.cos(localizer.heading)*deltaX + Math.sin(localizer.heading)*deltaY;
        yError = -Math.sin(localizer.heading)*deltaX + Math.cos(localizer.heading)*deltaY;
        turnError = targetPoint.heading-localizer.heading;

        while(Math.abs(turnError) > Math.PI ){
            turnError -= Math.PI * 2 * Math.signum(turnError);
        }
    }

    public void feedforward() {
        targetForwardPower = Math.min(Math.pow(Math.abs(xError)/xSlowdown, 2),1.0)*Math.signum(xError);
        targetStrafePower = Math.min(Math.pow(Math.abs(yError)/ySlowdown, 0.5),1.0)*Math.signum(yError);
        targetTurnPower = Math.min(Math.pow(Math.abs(turnError)/Math.toRadians(turnSlowdown), 2),1.0)*Math.signum(turnError);

        double fwd = Math.abs(xError) > xThreshold/2 ? targetForwardPower + kAccelX*(targetForwardPower - ROBOT_VELOCITY.x/(Globals.MAX_X_SPEED*(12/sensors.getVoltage()))) : 0;
        double strafe = Math.abs(yError) > yThreshold/2 ? targetStrafePower + kAccelY*(targetStrafePower - ROBOT_VELOCITY.y/(Globals.MAX_Y_SPEED*(12/sensors.getVoltage()))) : 0;
        double turn = Math.abs(turnError) > Math.toRadians(turnThreshold/2) ? targetTurnPower + kAccelTurn*(targetTurnPower - ROBOT_VELOCITY.heading/(Globals.MAX_HEADING_SPEED*(12/sensors.getVoltage()))) : 0;

        setMinPowersToOvercomeFriction();

        // electronic braking (turn off min power to overcome friction if we are braking)
        if (Math.abs(xError) < xBrakingDistanceThreshold && Math.abs(ROBOT_VELOCITY.x) > xBrakingSpeedThreshold) {
            fwd = xBrakingPower * Math.signum(xError);
            resetMinPowersToOvercomeFriction();
        }
//        if (Math.abs(yError) < yBrakingDistanceThreshold && Math.abs(ROBOT_VELOCITY.y) > yBrakingSpeedThreshold) {
//            strafe = yBrakingPower * Math.signum(yError);
//            resetMinPowersToOvercomeFriction();
//        }
        if (Math.abs(turnError) < Math.toRadians(turnBrakingAngleThreshold) && Math.abs(ROBOT_VELOCITY.heading) > Math.toRadians(turnBrakingSpeedThreshold)) {
            turn = turnBrakingPower * Math.signum(turnError);
            resetMinPowersToOvercomeFriction();
        }

        Vector2 move = new Vector2(fwd, strafe);
        setMoveVector(move, turn);

        if (atPoint()) {
            state = State.BRAKE;
        }
    }

    public void PIDF() {
        double fwd = Math.abs(xError) > xThreshold/2 ? xPID.update( xError) + 0.2 * Math.signum(xError) : 0;
        double strafe = Math.abs(yError) > yThreshold/2 ? yPID.update(yError) + 0.2 * Math.signum(yError) : 0;
        double turn = Math.abs(turnError) > Math.toRadians(turnThreshold)/2 ? turnPID.update(turnError) + 0.2 * Math.signum(turnError) : 0;

        Vector2 move = new Vector2(fwd, strafe);
        setMoveVector(move, turn);

        if (atPoint()) {
            state = State.BRAKE;
        }
    }

    public boolean isBusy() {
        return state != State.WAIT_AT_POINT;
    }

    public void stopAllMotors() {
        for (PriorityMotor motor : motors) {
            motor.setTargetPower(0);
        }
    }

    public void updateLocalizer() {
        localizer.updateEncoders(sensors.getOdometry());
        localizer.update();
    }

    public void updateTelemetry () {
        TelemetryUtil.packet.put("Drivetrain State", state);
        TelemetryUtil.packet.put("drivetrain at point", atPoint());

        TelemetryUtil.packet.put("xError", xError);
        TelemetryUtil.packet.put("yError", yError);
        TelemetryUtil.packet.put("turnError (deg)", Math.toDegrees(turnError));

        TelemetryUtil.packet.put("xTargetSpeed", targetForwardPower * Globals.MAX_X_SPEED);
        TelemetryUtil.packet.put("yTargetSpeed", targetStrafePower * Globals.MAX_Y_SPEED);
        TelemetryUtil.packet.put("turnTargetSpeed (deg)", targetTurnPower * Math.toDegrees(Globals.MAX_HEADING_SPEED));
    }

    public void goToPoint(Pose2d targetPoint) {
        TelemetryUtil.packet.fieldOverlay().setStroke("red");
        TelemetryUtil.packet.fieldOverlay().strokeCircle(targetPoint.x, targetPoint.y, xThreshold);

        if (targetPoint.x != lastTargetPoint.x || targetPoint.y != lastTargetPoint.y || targetPoint.heading != lastTargetPoint.heading) { // if we set a new target point we reset integral
            this.targetPoint = targetPoint;
            lastTargetPoint = targetPoint;
            state = State.GO_TO_POINT;
        }
    }

    public static double xThreshold = 1;
    public static double yThreshold = 1;
    public static double turnThreshold = 1.5;

    public void setBreakFollowingThresholds(Pose2d thresholds) {
        xThreshold = thresholds.getX();
        yThreshold = thresholds.getY();
        turnThreshold = thresholds.getHeading();
    }

    public boolean atPoint () {
        return Math.abs(xError) < xThreshold && Math.abs(yError) < yThreshold && Math.abs(turnError) < Math.toRadians(turnThreshold);
    }

    public void setMode(DcMotor.RunMode runMode) {
        for (PriorityMotor motor : motors) {
            motor.motor[0].setMode(runMode);
        }
    }

    public void setZeroPowerBehavior(DcMotor.ZeroPowerBehavior zeroPowerBehavior) {
        for (PriorityMotor motor : motors) {
            motor.motor[0].setZeroPowerBehavior(zeroPowerBehavior);
        }
    }

    public void setMotorPowers(double lf, double lr, double rr, double rf) {
        leftFront.setTargetPower(lf);
        leftRear.setTargetPower(lr);
        rightRear.setTargetPower(rr);
        rightFront.setTargetPower(rf);
    }

    private void normalizeArray(double[] arr) {
        double largest = 1;
        for (int i = 0; i < arr.length; i++) {
            largest = Math.max(largest, Math.abs(arr[i]));
        }
        for (int i = 0; i < arr.length; i++) {
            arr[i] /= largest;
        }
    }

    double strafeCorrectionFactor = 0.5/0.575;

    public void setMoveVector(Vector2 moveVector, double turn) {
        double[] powers = {
            moveVector.x - turn - moveVector.y * strafeCorrectionFactor,
            moveVector.x - turn + moveVector.y,
            moveVector.x + turn - moveVector.y,
            moveVector.x + turn + moveVector.y * strafeCorrectionFactor
        };
        normalizeArray(powers);

        TelemetryUtil.packet.put("leftFront", powers[0]);
        TelemetryUtil.packet.put("leftRear", powers[1]);
        TelemetryUtil.packet.put("rightRear", powers[2]);
        TelemetryUtil.packet.put("rightFront", powers[3]);

        setMotorPowers(powers[0], powers[1], powers[2], powers[3]);
    }

    public double smoothControls(double value) {
        return 0.5*Math.tan(1.12*value);
    }

    public void drive(Gamepad gamepad) {
        resetMinPowersToOvercomeFriction();

        state = State.DRIVE;

        double forward = smoothControls(gamepad.left_stick_y);
        double strafe = smoothControls(-gamepad.left_stick_x);
        double turn = smoothControls(gamepad.right_stick_x);

        double[] powers = {
            forward + turn + strafe * strafeCorrectionFactor,
            forward + turn - strafe,
            forward - turn + strafe,
            forward - turn - strafe * strafeCorrectionFactor
        };
        normalizeArray(powers);
        setMotorPowers(powers[0], powers[1], powers[2], powers[3]);
    }

    public Pose2d getPoseEstimate() {
        return localizer.getPoseEstimate();
    }

    public void setPoseEstimate(Pose2d pose2d) {
        localizer.setPoseEstimate(pose2d);
    }

    public Vector2 lineCircleIntersection(Pose2d start, Pose2d end, Pose2d robot, double radius) {
        //https://stackoverflow.com/questions/1073336/circle-line-segment-collision-detection-algorithm/1084899#1084899

        Vector2 direction = new Vector2(end.x - start.x, end.y - start.y);
        Vector2 robot2start = new Vector2(start.x - robot.x, start.y - robot.y);

        double a = Vector2.dot(direction, direction);
        double b = 2 * Vector2.dot(robot2start, direction);

        double c = Vector2.dot(robot2start, robot2start) - radius * radius;

        double discriminant = b * b - 4 * a * c;

        if (discriminant < 0) {
            return null;
        } else {
            discriminant = Math.sqrt(discriminant);
            double t1 = (-b + discriminant) / 2;
            double t2 = (-b - discriminant) / 2;

            if ((t1 >= 0) && (t1 <= 1)) {
                direction.mul(t1);
                return Vector2.add(direction, new Vector2(start.x, start.y));
            }
            if ((t2 >= 0) && (t2 <= 1)) {
                direction.mul(t2);
                return Vector2.add(direction, new Vector2(start.x, start.y));
            } else {
                return null;
            }
        }
    }
}