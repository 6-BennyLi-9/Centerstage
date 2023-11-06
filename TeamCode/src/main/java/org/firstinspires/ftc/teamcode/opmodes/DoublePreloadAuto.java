package org.firstinspires.ftc.teamcode.opmodes;

import com.acmerobotics.dashboard.config.Config;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.teamcode.Robot;
import org.firstinspires.ftc.teamcode.subsystems.drive.Drivetrain;
import org.firstinspires.ftc.teamcode.subsystems.drive.Spline;
import org.firstinspires.ftc.teamcode.utils.Globals;
import org.firstinspires.ftc.teamcode.utils.Pose2d;
import org.firstinspires.ftc.teamcode.utils.RunMode;
import org.firstinspires.ftc.teamcode.vision.Vision;
import org.firstinspires.ftc.teamcode.vision.pipelines.TeamPropDetectionPipeline;

@Config
@TeleOp
public class DoublePreloadAuto extends LinearOpMode {
    enum State {
        READY
    }


    private boolean up = false; // Is on top side of field


    private TeamPropDetectionPipeline.TEAM_PROP_LOCATION team_prop_location = TeamPropDetectionPipeline.TEAM_PROP_LOCATION.CENTER;

    @Override
    public void runOpMode() throws InterruptedException {
        Robot robot = new Robot(hardwareMap);
        Globals.RUNMODE = RunMode.AUTO;

        Vision vision = new Vision();
        TeamPropDetectionPipeline teamPropDetectionPipeline;

        // TODO: add initalization sequence
        teamPropDetectionPipeline = new TeamPropDetectionPipeline(telemetry, true);
        vision.initCamera(hardwareMap, teamPropDetectionPipeline);

        while (opModeInInit()) {
            team_prop_location = teamPropDetectionPipeline.getTeamPropLocation();

            telemetry.addData("leftAvg", teamPropDetectionPipeline.leftAvg);
            telemetry.addData("centerAvg", teamPropDetectionPipeline.centerAvg);
            telemetry.addData("rightAvg", teamPropDetectionPipeline.rightAvg);
            telemetry.addData("propLocation: ", team_prop_location);
            telemetry.update();
        }

        Spline initSpline = null;
        Spline leaveSpline = null;
        if (up) {
            robot.drivetrain.setPoseEstimate(new Pose2d(12, 60, Math.toRadians(90)));
        } else {
            robot.drivetrain.setPoseEstimate(new Pose2d(-36, 60, Math.toRadians(90))); // up and down are mixed together
        }

        // Wubba lubba dub dub
        Pose2d pose = robot.drivetrain.getPoseEstimate();
        switch (team_prop_location) {
            case LEFT:
                initSpline = new Spline(pose, 4)
                        .addPoint(new Pose2d(pose.x, pose.y - 24, Math.toRadians(0)));
                leaveSpline = new Spline(initSpline.getLastPoint(), 4)
                        .setReversed(true)
                        .addPoint(new Pose2d(pose.x - 12, pose.y - 24, 0))
                        .setReversed(false)
                        .addPoint(new Pose2d(pose.x, pose.y-36, Math.toRadians(-90)));
                break;
            case CENTER:
                initSpline = new Spline(pose, 4)
                        .addPoint(new Pose2d(pose.x, pose.y - 24, Math.toRadians(-90)));
                if (!up) {
                    leaveSpline = new Spline(initSpline.getLastPoint(), 4)
                            .setReversed(true)
                            .addPoint(new Pose2d(pose.x, pose.y - 12, Math.toRadians(-90)))
                            .addPoint(new Pose2d(pose.x - 24, pose.y - 24, Math.toRadians(-90)))
                            .addPoint(new Pose2d(pose.x - 24, pose.y - 48, Math.toRadians(0)));
                }
                else {
                    leaveSpline = new Spline(initSpline.getLastPoint(), 4)
                            .addPoint(new Pose2d(pose.x + 12, pose.y - 24, 0));
                }
                break;
            case RIGHT:
                initSpline = new Spline(pose, 4)
                        .addPoint(new Pose2d(pose.x, pose.y - 24, Math.toRadians(-180)));
                leaveSpline = new Spline(initSpline.getLastPoint(), 4)
                        .addPoint(new Pose2d(pose.x, pose.y-48, 0));
                break;
            default:
                // CRASH CRASH BAD BAD!
                RobotLog.e("BAD BAD! CRASH! SOMETHING TERRIBLE HAPPENED! GET HUDSON!");
                break;
        }

        Spline toSide = new Spline(leaveSpline.getLastPoint(), 4)
                .addPoint(new Pose2d(36, 12, 0));

        Spline toDeposit = new Spline(toSide.getLastPoint(), 4)
                .addPoint(new Pose2d(40, 36, 0));

        Spline toPark = new Spline(toDeposit.getLastPoint(), 4)
                .setReversed(true)
                .addPoint(new Pose2d(36, 12, 0))
                .setReversed(false)
                .addPoint(new Pose2d(60,12,0));


        robot.deposit.setTargetBoard(new Pose2d(64, 36, 0));

        waitForStart();

       robot.followSpline(initSpline, this);

       robot.intake.reverse();
       long time = System.currentTimeMillis();
       while (System.currentTimeMillis() - time < 3000) {
           break;
       }
       robot.intake.off();

       robot.followSpline(leaveSpline, this);
       robot.followSpline(toSide, this);

        double y;
       if (team_prop_location == TeamPropDetectionPipeline.TEAM_PROP_LOCATION.LEFT) {
           y = 4.5;
       }
       else if (team_prop_location == TeamPropDetectionPipeline.TEAM_PROP_LOCATION.RIGHT) {
           y = -4.5;
       }
       else {
           y = 0;
       }
       robot.deposit.depositAt(10, y, 3, 0, 0);
       robot.followSpline(toDeposit, this);
       robot.deposit.inPlace();
       robot.deposit.dunk(1);
       robot.followSpline(toPark, this);



    }
}
