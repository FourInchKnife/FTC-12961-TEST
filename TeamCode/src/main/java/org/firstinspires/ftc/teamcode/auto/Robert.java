package org.firstinspires.ftc.teamcode.auto;

import android.annotation.SuppressLint;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.trajectory.Trajectory;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.teamcode.drive.PoseStorage;
import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.openftc.easyopencv.OpenCvCameraFactory;
import org.openftc.easyopencv.OpenCvCameraRotation;
import org.openftc.easyopencv.OpenCvPipeline;
import org.openftc.easyopencv.OpenCvWebcam;

public class Robert extends LinearOpMode {
    private final ElapsedTime runtime = new ElapsedTime();
    OpenCvWebcam webCam;
    AutoWithVision.RingDeterminationPipeline pipeline;

    Trajectory clearance, flip, toGoal, toLine;

    @SuppressLint("DefaultLocale")
    @Override
    public void runOpMode() throws InterruptedException {
        telemetry.setAutoClear(false);
        Telemetry.Item initItem = telemetry.addData("Initializing...", "Setting up hardware");
        telemetry.update();

        PoseStorage.currentPose = new Pose2d(-62, -21, Math.toRadians(0));

        // RR stuff
        SampleMecanumDrive drive = new SampleMecanumDrive(hardwareMap);
        Pose2d startPose = PoseStorage.currentPose;
        drive.setPoseEstimate(startPose);

        Telemetry.Item xItem = telemetry.addData("x", drive.getPoseEstimate().getX());
        Telemetry.Item yItem = telemetry.addData("y", drive.getPoseEstimate().getY());
        Telemetry.Item headingItem = telemetry.addData("θ", drive.getPoseEstimate().getHeading());

        int onTrajBuild = 1;
        Telemetry.Item trajBuildItem = telemetry.addData("Building", onTrajBuild);
        onTrajBuild++;
        telemetry.update();

        initItem.setValue("Starting camera feed");
        telemetry.update();
        // Camera stuff
        int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        webCam = OpenCvCameraFactory.getInstance().createWebcam(hardwareMap.get(WebcamName.class, "camra"), cameraMonitorViewId);
        pipeline = new AutoWithVision.RingDeterminationPipeline();
        webCam.setPipeline(pipeline);

        //listens for when the camera is opened
        webCam.openCameraDeviceAsync(() -> {
            //if the camera is open start steaming
            webCam.startStreaming(320, 240, OpenCvCameraRotation.UPRIGHT);
        });

        initItem.setValue("Checking ring position");
        telemetry.update();
        telemetry.addData("RingPosGuess", pipeline.position);

        initItem.setValue("Building trajectories");


        waitForStart();

        if (isStopRequested()) return;
        telemetry.removeItem(initItem);
        double initTime = runtime.milliseconds();

        Telemetry.Item runtimeItem = telemetry.addData(
                "Runtime",
                String.format(
                        "%fms",
                        runtime.milliseconds() - initTime
                ));
        telemetry.update();

        drive.setWobblePosPow(0, 1, 0);
        sleep(1000);
        drive.setWobblePosPow(-1, 0, 0);
        sleep(1000);
        drive.setWobblePosPow(0, -1, 0);
        sleep(1000);

        drive.followTrajectoryAsync(clearance);
        /*Note avg1 measures the orange content of the BoundingBox.
        If it's above BoundingBoxPos.FourRingThresh then there are four rings.
        If it's between BoundingBosPos.FourRingThresh and BoundingBoxPos.OneRingThresh then there is one ring.
        Otherwise there are no rings.
         */
        while (opModeIsActive() && !isStopRequested()) {
            drive.update();
            runtimeItem.setValue(
                    String.format(
                            "%fms",
                            runtime.milliseconds() - initTime
                    ));
            Pose2d tempPose = drive.getPoseEstimate();
            xItem.setValue(tempPose.getX());
            yItem.setValue(tempPose.getY());
            headingItem.setValue(tempPose.getHeading());
            telemetry.update();
        }
    }

    public static class RingDeterminationPipeline extends OpenCvPipeline {
        /*
         * An enum to define the Ring position
         */
        public enum RingPosition {
            FOUR,
            ONE,
            NONE
        }


        //Some color constants
        static final Scalar BLUE = new Scalar(0, 0, 255);
        static final Scalar GREEN = new Scalar(0, 255, 0);

        //The core values which define the location and size of the sample regions

        //This point is the location of the top-left corner of the bounding box
        Point REGION1_TOPLEFT_ANCHOR_POINT = new Point(BoundingBoxPos.TopLeftX, BoundingBoxPos.TopLeftY);

        //These variables measure the width and height of the bounding box
        int REGION_WIDTH = BoundingBoxPos.Width;
        int REGION_HEIGHT = BoundingBoxPos.Height;

        //These constants should be how much orange is in the bounding box when it has a number of rings
        final int FOUR_RING_THRESHOLD = BoundingBoxPos.FourRingThresh;
        final int ONE_RING_THRESHOLD = BoundingBoxPos.OneRingThresh;

        //This point defines the top left corner of the bounding box
        Point region1_pointA = new Point(
                REGION1_TOPLEFT_ANCHOR_POINT.x,
                REGION1_TOPLEFT_ANCHOR_POINT.y);
        //This point defines the bottom right corner of the bounding box. It is REGION_WIDTH right and REGION_HEIGHT below of the top left corner
        Point region1_pointB = new Point(
                REGION1_TOPLEFT_ANCHOR_POINT.x + REGION_WIDTH,
                REGION1_TOPLEFT_ANCHOR_POINT.y + REGION_HEIGHT);

        /*
         * Working variables
         */
        //YCrCb is a additive color family where Y measures the value, Cr measures red, and Cb measures blue.
        Mat region1_Cb;
        Mat YCrCb = new Mat();
        Mat Cb = new Mat();
        int avg1;

        // Volatile since accessed by OpMode thread w/o synchronization
        public volatile AutoWithVision.RingDeterminationPipeline.RingPosition position = AutoWithVision.RingDeterminationPipeline.RingPosition.FOUR;

        /*
         * This function takes the RGB frame, converts to YCrCb,
         * and extracts the Cb channel to the 'Cb' variable
         */
        //This program only uses Cb, because Cb can measure a greenish orange which is close to the ring color.
        void inputToCb(Mat input) {
            Imgproc.cvtColor(input, YCrCb, Imgproc.COLOR_RGB2YCrCb);
            Core.extractChannel(YCrCb, Cb, 1);
        }

        @Override
        public void init(Mat firstFrame) {
            inputToCb(firstFrame);

            region1_Cb = Cb.submat(new Rect(region1_pointA, region1_pointB));
        }

        @Override
        public Mat processFrame(Mat input) {
            inputToCb(input);

            avg1 = (int) Core.mean(region1_Cb).val[0];

            Imgproc.rectangle(
                    input, // Buffer to draw on
                    region1_pointA, // First point which defines the rectangle
                    region1_pointB, // Second point which defines the rectangle
                    BLUE, // The color the rectangle is drawn in
                    2); // Thickness of the rectangle lines

            position = AutoWithVision.RingDeterminationPipeline.RingPosition.FOUR; // Record our analysis
            if (avg1 > FOUR_RING_THRESHOLD) {
                position = AutoWithVision.RingDeterminationPipeline.RingPosition.FOUR;
            } else if (avg1 > ONE_RING_THRESHOLD) {
                position = AutoWithVision.RingDeterminationPipeline.RingPosition.ONE;
            } else {
                position = AutoWithVision.RingDeterminationPipeline.RingPosition.NONE;
            }

            Imgproc.rectangle(
                    input, // Buffer to draw on
                    region1_pointA, // First point which defines the rectangle
                    region1_pointB, // Second point which defines the rectangle
                    GREEN, // The color the rectangle is drawn in
                    -1); // Negative thickness means solid fill
            return input;
        }

        public int getAnalysis() {
            return avg1;
        }
    }
}