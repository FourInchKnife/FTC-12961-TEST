/*
 * Copyright (c) 2020 OpenFTC Team
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.firstinspires.ftc.teamcode.auto;

import android.annotation.SuppressLint;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
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

@Autonomous
public class RingAmountTest extends LinearOpMode {
    private final ElapsedTime runtime = new ElapsedTime();
    OpenCvWebcam webCam;
    RingDeterminationPipeline pipeline;
    private static RingDeterminationPipeline.RingPosition position = RingDeterminationPipeline.RingPosition.FOUR;

    @SuppressLint("DefaultLocale")
    @Override
    public void runOpMode() throws InterruptedException
    {
        telemetry.setAutoClear(false);
        Telemetry.Item initItem = telemetry.addData("Initializing...","Setting up hardware");
        telemetry.update();

        initItem.setValue("Starting camera feed");
        telemetry.update();
        // Camera stuff
        int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        webCam = OpenCvCameraFactory.getInstance().createWebcam(hardwareMap.get(WebcamName.class, "camra"), cameraMonitorViewId);
        pipeline = new RingDeterminationPipeline();
        webCam.setPipeline(pipeline);

        //listens for when the camera is opened
        webCam.openCameraDeviceAsync(() -> {
            //if the camera is open start steaming
            webCam.startStreaming(320,240, OpenCvCameraRotation.UPRIGHT  );
        });

        initItem.setValue("Checking ring position");
        telemetry.update();

        int ringPos = pipeline.getAnalysis();
        Telemetry.Item ringPosItem = telemetry.addData("RingPosGuess",ringPos);
        Telemetry.Item ringAmountItem = telemetry.addData("RingAmountGuess",position);



        initItem.setValue(String.format("Done. Took %f milliseconds",runtime.milliseconds()));
        telemetry.update();

        waitForStart();

        if(isStopRequested()) return;
        telemetry.removeItem(initItem);
        double initTime = runtime.milliseconds();

        Telemetry.Item runtimeItem = telemetry.addData(
                "Runtime",
                String.format(
                        "%fms",
                        runtime.milliseconds()-initTime
                ));

        while (opModeIsActive() && !isStopRequested()) {
            runtimeItem.setValue(
                    String.format(
                            "%fms",
                            runtime.milliseconds()-initTime
                    ));
            ringPos = pipeline.getAnalysis();
            ringPosItem.setValue(ringPos);
            telemetry.update();

        }
    }
    public static class RingDeterminationPipeline extends OpenCvPipeline
    {
        /*
         * An enum to define the Ring position
         */
        public enum RingPosition
        {
            FOUR,
            ONE,
            NONE
        }


        //Some color constants
        static final Scalar BLUE = new Scalar(0, 0, 255);
        static final Scalar GREEN = new Scalar(0, 255, 0);

        //The core values which define the location and size of the sample regions

        Point REGION1_TOPLEFT_ANCHOR_POINT = new Point(BoundingBoxPos.TopLeftX, BoundingBoxPos.TopLeftY);

        int REGION_WIDTH = BoundingBoxPos.Width;
        int REGION_HEIGHT = BoundingBoxPos.Height;

        final int FOUR_RING_THRESHOLD = BoundingBoxPos.FourRingThresh;
        final int ONE_RING_THRESHOLD = BoundingBoxPos.OneRingThresh;

        Point region1_pointA = new Point(
                REGION1_TOPLEFT_ANCHOR_POINT.x,
                REGION1_TOPLEFT_ANCHOR_POINT.y);
        Point region1_pointB = new Point(
                REGION1_TOPLEFT_ANCHOR_POINT.x + REGION_WIDTH,
                REGION1_TOPLEFT_ANCHOR_POINT.y + REGION_HEIGHT);

        /*
         * Working variables
         */
        Mat region1_Cb;
        Mat YCrCb = new Mat();
        Mat Cb = new Mat();
        int avg1;

        // Volatile since accessed by OpMode thread w/o synchronization

        /*
         * This function takes the RGB frame, converts to YCrCb,
         * and extracts the Cb channel to the 'Cb' variable
         */
        void inputToCb(Mat input)
        {
            Imgproc.cvtColor(input, YCrCb, Imgproc.COLOR_RGB2YCrCb);
            Core.extractChannel(YCrCb, Cb, 1);
        }

        @Override
        public void init(Mat firstFrame)
        {
            inputToCb(firstFrame);

            region1_Cb = Cb.submat(new Rect(region1_pointA, region1_pointB));
        }

        @Override
        public Mat processFrame(Mat input)
        {
            inputToCb(input);

            avg1 = (int) Core.mean(region1_Cb).val[0];

            Imgproc.rectangle(
                    input, // Buffer to draw on
                    region1_pointA, // First point which defines the rectangle
                    region1_pointB, // Second point which defines the rectangle
                    BLUE, // The color the rectangle is drawn in
                    2); // Thickness of the rectangle lines

            position = RingPosition.FOUR; // Record our analysis
            if(avg1 > FOUR_RING_THRESHOLD){
                position = RingPosition.FOUR;
            }else if (avg1 > ONE_RING_THRESHOLD){
                position = RingPosition.ONE;
            }else{
                position = RingPosition.NONE;
            }

            Imgproc.rectangle(
                    input, // Buffer to draw on
                    region1_pointA, // First point which defines the rectangle
                    region1_pointB, // Second point which defines the rectangle
                    GREEN, // The color the rectangle is drawn in
                    -1); // Negative thickness means solid fill
            return input;
        }

        public int getAnalysis()
        {
            return avg1;
        }
    }
}
