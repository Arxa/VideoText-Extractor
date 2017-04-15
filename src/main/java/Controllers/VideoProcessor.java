package Controllers;

import libsvm.*;

import static Models.ImageContainer.*;

import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import java.util.List;


/**
 * Created by arxa on 16/11/2016.
 */

public class VideoProcessor
{
    private static VideoCapture cap;

    // TODO Handle the Exceptions
    public static void processVideo(Pane pane2, Label label1)
    {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME); // Required for OpenCV libraries usage
        cap = new VideoCapture(Player.getFilename().toString());

        if (cap.isOpened())
        {
            Task<Void> task = new Task<Void>()
            {
                @Override protected Void call() throws Exception
                {
                    Writer.initializeVideoWriter();
                    init(); // Using Static import of ImageContainer here and in code below!
                    boolean open1 = cap.read(getInput());

                    while (true)
                    {
                        if (open1)
                        {
                            setInput_GB(new Mat(getInput().size(),getInput().type()));

                            Imgproc.GaussianBlur(getInput(),getInput_GB(),
                                    new Size(15.0,15.0),0.0,0.0);

                            Imgproc.cvtColor(getInput_GB(), getInput_GB_Gray(), Imgproc.COLOR_RGB2GRAY, 0);

                            Imgproc.Laplacian(getInput_GB_Gray(),getInput_GB_Gray_LPL(),
                                    CvType.CV_16S,1,1,0); //bigger ksize stronger intensity

                            // array allocation happens inside 'matToArray' method
                            setInput_GB_Gray_LPL_in_Array
                                    (PixelProcessor.matToArray(getInput_GB_Gray_LPL()));

                            setInput_GB_Gray_LPL_MGD_in_Array(PixelProcessor.
                                    find_MaximumGradientDifference(getInput_GB_Gray_LPL_in_Array(),
                                            getInput_GB_Gray_LPL().height(),
                                                    getInput_GB_Gray_LPL().width()));

                            setInput_GB_Gray_LPL_MGD(PixelProcessor.
                                    arrayToMat(getInput_GB_Gray_LPL_MGD_in_Array(),
                                            getInput_GB_Gray_LPL().height(),getInput_GB_Gray_LPL().width(),CvType.CV_16S));

                            getInput_GB_Gray_LPL_MGD_NORM().create
                                    (getInput_GB_Gray_LPL_MGD().size(),CvType.CV_32FC1);

                            Core.normalize(getInput_GB_Gray_LPL_MGD(),
                                    getInput_GB_Gray_LPL_MGD_NORM(),0.0,1.0,
                                            Core.NORM_MINMAX, getInput_GB_Gray_LPL_MGD_NORM().type());

                            // Make Classification of 0~1 normalized values into 0 and 1
                            setInput_GB_Gray_LPL_MGD_NORM_KMEANS
                                    (Clustering.k_Means(getInput_GB_Gray_LPL_MGD_NORM()));

                            // Initializing 'Binary' image filled with zeros (black)
                            setInput_GB_Gray_LPL_MGD_NORM_KMEANS_BIN
                                    (new Mat(getInput_GB_Gray_LPL_MGD_NORM().size(),
                                            CvType.CV_8UC1,new Scalar(0.0)));

                            // Fill the K-MEANS text cluster values to binary image above as ones (white)
                            Visualizer.paintMatToBinary(getInput_GB_Gray_LPL_MGD_NORM_KMEANS(),
                                                    getInput_GB_Gray_LPL_MGD_NORM_KMEANS_BIN());

                            // Initialize image for the Dilation operation
                            setInput_GB_Gray_LPL_MGD_NORM_KMEANS_BIN_DILATED(
                                    new Mat(getInput_GB_Gray_LPL_MGD_NORM_KMEANS_BIN().rows(),
                                            getInput_GB_Gray_LPL_MGD_NORM_KMEANS_BIN().cols(),
                                                    getInput_GB_Gray_LPL_MGD_NORM_KMEANS_BIN().type()));

                            // Do Dilation
                            Imgproc.dilate(getInput_GB_Gray_LPL_MGD_NORM_KMEANS_BIN(),
                                    getInput_GB_Gray_LPL_MGD_NORM_KMEANS_BIN_DILATED(),
                                        Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(13.0,13.0)));
                                        // Defaults: 3x3 - Anchor: center

                            // Calculate Sobel Edges of original image - needed below
                            Imgproc.Sobel(getInput(),getInput_Sobel(),CvType.CV_8U,1,1);

                            // Find and Filter Text Rect Blocks - with Sobel's help
                            List<Rect> textBlocks = Clustering.find_TextBlocks
                                    (getInput_GB_Gray_LPL_MGD_NORM_KMEANS_BIN_DILATED());



                            // TODO consider changing the size of every new Mat to original input's only



                            Training.init();
                            Training.create_labels();
                            Training.read_training_data("text");
                            Training.read_training_data("background");

                            Mat train_norm = new Mat();

                            /*Core.normalize(Training.getTraining_set(), train_norm, 0.0,1.0,
                                    Core.NORM_MINMAX, Training.getTraining_set().type());*/

                            System.out.println("Reading complete. Setting SVM parameters");

                            SupportVectorMachine.setParameters();

                            System.out.println("Training has begun.");

                            double[][] trainArray = PixelProcessor.matToArray(Training.getTraining_set());
                            double[] labelsArray = PixelProcessor.matTo1dArray(Training.getLabels());

                            SupportVectorMachine.createProblem(trainArray,labelsArray);
                            //SupportVectorMachine.gridSearch();
                            SupportVectorMachine.testGridSearch();

                            //SupportVectorMachine.train(trainArray,labelsArray);

                            //SupportVectorMachine.save("file1.xml",


                            System.out.println("Trained.");

                            // Paint the approved text blocks to the original image
                            //Visualizer.paintBlocksToOriginalImage(rects, getInput());

                            //PixelProcessor.printTrainArray(Training.getTraining_set());
                            //MatProcessor.printMat(Training.getLabels());

                            // Create Image file
                            //Writer.writeFrameAsImage(getInput());

                            System.out.println("Cycle completed");
                            init();

                            open1 = cap.read(getInput());

                            open1 = false;


                        }
                        else break;
                    } return null;
                }
                @Override protected void succeeded() {
                    super.succeeded();
                    SystemController.closeVideoHandlers();
                    System.out.println("Everything is done!");
                    ViewController.processIsComplete(pane2,label1);
                }
                @Override protected void cancelled() {
                    super.cancelled();
                    updateMessage("Thread has been cancelled!");
                }
                @Override protected void failed() {
                    super.failed();
                    updateMessage("Thread has failed!");
                }
            };
            Thread th = new Thread(task);
            th.setDaemon(true);
            th.start();
        }
    }

    public static VideoCapture getCap() {
        return cap;
    }
}
