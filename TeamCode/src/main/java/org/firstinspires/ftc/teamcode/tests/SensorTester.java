package org.firstinspires.ftc.teamcode.tests;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.firstinspires.ftc.teamcode.Robot;
import org.firstinspires.ftc.teamcode.sensors.Sensors;

@TeleOp
public class SensorTester extends LinearOpMode {
    @Override
    public void runOpMode() throws InterruptedException {
        Robot robot = new Robot(hardwareMap);
        Sensors sensors = robot.sensors;
        waitForStart();

        while (!isStopRequested()) {
            robot.update();

            telemetry.addData("deltaLeft", sensors.getOdometry()[0]);
            telemetry.addData("deltaRight", sensors.getOdometry()[1]);
            telemetry.addData("deltaBack", sensors.getOdometry()[2]);

            telemetry.update();
        }
    }
}
