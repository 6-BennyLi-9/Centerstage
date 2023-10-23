package org.firstinspires.ftc.teamcode.opmodes;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

import org.firstinspires.ftc.teamcode.Robot;
import org.firstinspires.ftc.teamcode.subsystems.drive.Drivetrain;
import org.firstinspires.ftc.teamcode.subsystems.drive.Spline;
import org.firstinspires.ftc.teamcode.utils.Pose2d;

// The following auto does NOT do the init

@Autonomous(group = "opmodes", name = "Auto (Good luck!)")
public class Auto extends LinearOpMode {
    enum State {
        START_INTAKE_START,
        START_INTAKE_CYCLE,
        INTAKE,
        START_GO_DEPOSIT_START,
        START_GO_DEPOSIT_CYCLE,
        GO_DEPOSIT,
        DUNK_EM,
        READY
    }

    private State state = State.READY;
    private Drivetrain.State heldDrivetrainState = null;
    private boolean up = false; // Is on top side of field
    private boolean blockedDepositPath = false;
    private boolean changingPaths = true;

    @Override
    public void runOpMode() throws InterruptedException {
        Robot robot = new Robot(hardwareMap);

        waitForStart();
        state = State.START_INTAKE_START;
        if (up) {
            robot.drivetrain.setPoseEstimate(new Pose2d(-36, 60, Math.toRadians(90))); // up and down are mixed together
        } else {
            robot.drivetrain.setPoseEstimate(new Pose2d(12, 60, Math.toRadians(90)));
        }
        //todo add the step to place preload + if the preload was in left or right

        Spline toIntakeStart = new Spline(new Pose2d(-36, 36, Math.toRadians(90)), 4) // doesn't have up variant
            .addPoint(new Pose2d(-60, 36, Math.toRadians(180)));

        Spline toIntakeCycle = new Spline(new Pose2d(48, 36, Math.toRadians(180)), 4)
            .setReversed(true)
            .addPoint(new Pose2d(-60, 36, Math.toRadians(180)));

        Spline toIntakeCycleBlocked = new Spline(new Pose2d(48, 36, Math.toRadians(180)), 4)
            .setReversed(true)
            .addPoint(new Pose2d(48, 12, Math.toRadians(150)))
            .addPoint(new Pose2d(-48, 12, Math.toRadians(180)))
            .addPoint(new Pose2d(-60, 36, Math.toRadians(120))); //todo figure the turns out

        // An alliance is blocking us from going to the board
        Spline toDepositStart = new Spline(new Pose2d(12, 36, 0), 4)
            .addPoint(new Pose2d(48, 36, Math.toRadians(180)));

        // TODO: unsure path
        Spline blockedToDeposit = new Spline(toIntakeStart.getLastPoint(), 4)
            .setReversed(true)
            .addPoint(new Pose2d(-60, 12, Math.toRadians(225)))
            .addPoint(new Pose2d(36, 12, Math.toRadians(180)))
            .addPoint(new Pose2d(48, 36, Math.toRadians(180)));
        Spline toDeposit = new Spline(toIntakeStart.getLastPoint(), 4)
            .setReversed(true)
            .addPoint(new Pose2d(48, 36, Math.toRadians(180)));

        while (opModeIsActive()) {
            switch (state) {
                case START_INTAKE_START:
                    // Don't run this if WE UP
                    robot.drivetrain.setCurrentPath(toIntakeStart);
                    state = State.INTAKE;
                    break;

                case START_INTAKE_CYCLE:
                    robot.drivetrain.setCurrentPath(/* robot detected */ false ? toIntakeCycleBlocked : toIntakeCycle);
                    state = State.INTAKE;
                    break;

                case INTAKE:
                    if (/* robot detected */ false) {
                        // Store state so we can restore it after
                        if (heldDrivetrainState != null) {
                            heldDrivetrainState = robot.drivetrain.state;
                            robot.drivetrain.state = Drivetrain.State.BRAKE;
                        }
                    } else if (heldDrivetrainState != null) {
                        robot.drivetrain.state = heldDrivetrainState;
                        heldDrivetrainState = null;
                    }

                    if (/* test for pixels intaken */ true) {
                        state = State.START_GO_DEPOSIT_CYCLE;
                    }
                    break;

                case START_GO_DEPOSIT_START:
                    robot.drivetrain.setCurrentPath(toDepositStart);
                    state = State.GO_DEPOSIT;
                    break;

                case START_GO_DEPOSIT_CYCLE:
                    robot.drivetrain.setCurrentPath(/* robot detected */ false ? blockedToDeposit : toDeposit);
                    blockedDepositPath = false;
                    state = State.GO_DEPOSIT;
                    break;

                // This state is ancient shadow magic - Eric
                case GO_DEPOSIT:
                    Pose2d pose = robot.drivetrain.localizer.getPoseEstimate();
                    // Switch auto paths if robot detected and we are outside of special zone
                    if ((pose.x > 4 && pose.x < 44) || pose.x < -30) {
                        if (/* robot detected */ false && !changingPaths) {
                            if (heldDrivetrainState == null) {
                                heldDrivetrainState = robot.drivetrain.state;
                                // In order to do moveToPoint we have to make robot.update() set no motor powers. Kyle is a bastard - Eric
                                robot.drivetrain.state = Drivetrain.State.DRIVE; // This state makes it do nothing. We like writing jank code here - Eric :)
                            }
                            blockedDepositPath = !blockedDepositPath; // Set the new path we are changing to
                            changingPaths = false; // We boutta strafe now ong
                        } else { // No robot detected and we are changing paths means that we need to stop changing paths and move to the correct path
                            robot.drivetrain.state = heldDrivetrainState;
                            heldDrivetrainState = null;
                            robot.drivetrain.setCurrentPath(blockedDepositPath ? blockedToDeposit : toDeposit);
                            changingPaths = true;
                        }
                    } else if (/* robot detected */ false) { // We are in the dead zone
                        if (heldDrivetrainState == null) {
                            heldDrivetrainState = robot.drivetrain.state;
                            robot.drivetrain.state = Drivetrain.State.BRAKE;
                        }
                    } else if (heldDrivetrainState != null) {
                        // Restore state
                        robot.drivetrain.state = heldDrivetrainState;
                        heldDrivetrainState = null;
                    }

                    if (changingPaths) {
                        robot.drivetrain.goToPoint(new Pose2d(pose.x, blockedDepositPath ? 12 : 36, 0));
                    }

                    if (!robot.drivetrain.isBusy())
                        state = State.DUNK_EM;

                    break;

                case DUNK_EM:
                    // This is a placeholder state for robot FSM usage (tee hee)
                    // Dunk em
                    state = State.START_INTAKE_CYCLE;
                    break;
            }
            robot.update();
        }
    }
}
