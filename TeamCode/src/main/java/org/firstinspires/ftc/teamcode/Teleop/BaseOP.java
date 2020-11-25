package org.firstinspires.ftc.teamcode.Teleop;

import android.annotation.SuppressLint;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.drive.PoseStorage;
import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;


@TeleOp(group = "TeleOP")
public class BaseOP extends LinearOpMode {
    private final ElapsedTime runtime = new ElapsedTime();
    @Override
    public void runOpMode() throws InterruptedException {
        // Initialize SampleMecanumDrive
        SampleMecanumDrive drive = new SampleMecanumDrive(hardwareMap);

        // We want to turn off velocity control for teleop
        // Velocity control per wheel is not necessary outside of motion profiled auto
        drive.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);

        // Retrieve our pose from the PoseStorage.currentPose static field
        // See AutoTransferPose.java for further details
        drive.setPoseEstimate(PoseStorage.currentPose);

        waitForStart();

        if (isStopRequested()) return;
        while (opModeIsActive() && !isStopRequested()) {
            controlRobo(drive, 0,false);
        }
        PoseStorage.currentPose = drive.getPoseEstimate();
    }
    private void controlRoboBase(SampleMecanumDrive robot, double rotationalOffset, boolean relative) {
        // Read pose
        Pose2d poseEstimate = robot.getPoseEstimate();
        double offset;
        if (relative) {
            offset = -rotationalOffset - poseEstimate.getHeading();
        } else {
            offset = 0;
        }

        // Create a vector from the gamepad x/y inputs
        // Then, rotate that vector by the inverse of that heading
        Vector2d input = new Vector2d(
                -gamepad1.left_stick_y,
                -gamepad1.left_stick_x
        ).rotated(offset);

        // Pass in the rotated input + right stick value for rotation
        // Rotation is not part of the rotated input thus must be passed in separately
        robot.setWeightedDrivePower(
                new Pose2d(
                        input.getX(),
                        input.getY(),
                        -gamepad1.right_stick_x
                )
        );

        // Control the intake motors
        int intakePower = 0;
        int transferPower = 0;
        if (gamepad2.a) {
            transferPower += 1;
        }
        if (gamepad2.b) {
            transferPower -= 1;
        }
        if (gamepad2.y) {
            intakePower += 1;
        }
        if (gamepad2.x) {
            intakePower -= 1;
        }
        robot.setIntakePowers(intakePower * -0.5, transferPower * .5, transferPower * .5);

        // Control the wobble bits
        int grab = 0;
        if (gamepad2.right_bumper) {
            grab += 1;
        }
        if (gamepad2.left_bumper) {
            grab -= 1;
        }

        int arm = 0;
        if (gamepad2.dpad_down) {
            arm += 1;
        }
        if (gamepad2.dpad_up) {
            arm -= 1;
        }

        robot.setWobblePosPow(grab, arm, gamepad2.left_stick_y);

        // Update everything. Odometry. Etc.
        robot.update();
    }
    @SuppressLint("DefaultLocale")
    public void controlRobo(SampleMecanumDrive robot, double rotationalOffset, boolean relative, ElapsedTime runtime) {
        controlRoboBase(robot, rotationalOffset, relative);
        Pose2d poseEstimate = robot.getPoseEstimate();
        // Print pose to telemetry
        telemetry.addData("x", poseEstimate.getX());
        telemetry.addData("y", poseEstimate.getY());
        telemetry.addData("heading", poseEstimate.getHeading());
        telemetry.addData("runtime",String.format("%fms",runtime.milliseconds()));
        telemetry.update();
    }
    public void controlRobo(SampleMecanumDrive robot, double rotationalOffset, boolean relative) {
        controlRoboBase(robot, rotationalOffset, relative);
        Pose2d poseEstimate = robot.getPoseEstimate();
        // Print pose to telemetry
        telemetry.addData("x", poseEstimate.getX());
        telemetry.addData("y", poseEstimate.getY());
        telemetry.addData("heading", poseEstimate.getHeading());
        telemetry.update();
    }
}