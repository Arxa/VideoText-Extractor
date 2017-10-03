package Processors;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by arxa on 2/4/2017.
 */

public class MatProcessor
{
    /**
     * Crops areas from the original image which correspond to the given Rect blocks
     * @param textBlocks The Rect text blocks list
     * @return A list of Mat crops
     */
    public static List<Mat> getTextBlocksAsMat(List<Rect> textBlocks)
    {
        List<Mat> textRegions = new ArrayList<>();
        for (Rect r : textBlocks) {
            Mat crop = new Mat(VideoProcessor.getInput(),r);
            textRegions.add(crop);
        }
        return textRegions;
    }

    /**
     * Finds the text block areas by filtering the connected components of the dilated Mat image.
     * @param dilated The dilated Mat image
     * @return A List of Rect representing the finalist text block areas
     */
    public static List<Rect> find_TextBlocks(Mat dilated)
    {
        List<Rect> textBlocks = new ArrayList<>();
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        int numberOfLabels = Imgproc.connectedComponentsWithStats(dilated,labels,stats,centroids,8, CvType.CV_32S);

        // Label 0 is considered to be the background label, so we skip it
        for (int i=1; i<numberOfLabels; i++)
        {
            /*
             * stats columns; [0-4] : [left top width height area}
             * Applying the first filters; a connected component is only accepted if;
             * its width is more than twice as big than its height AND
             * its area is greater than the product of its height, width and 0.004
             */
            if ( Double.compare(stats.get(i,2)[0]/stats.get(i,3)[0],2.0) > 0 &&
                    Double.compare(stats.get(i,4)[0],(dilated.height()*dilated.width())*0.004 ) > 0)
            {
                /*
                 * Applying the second filters; a connected component is accepted only if;
                 * its area corresponds to enough Canny edges (see method's description)
                 */
                if (PixelProcessor.areaIsSobelDense(stats.get(i,0)[0],stats.get(i,1)[0],
                        stats.get(i,2)[0],stats.get(i,3)[0],stats.get(i,4)[0]))
                {
                    textBlocks.add(new Rect(new Point(stats.get(i,0)[0],stats.get(i,1)[0]),new Size(stats.get(i,2)[0],
                            stats.get(i,3)[0])));
                }
            }
        }
        return textBlocks;
    }

    /**
     * Paints with red the text block boundaries of the original image
     * @param textBlocks The list of text blocks
     * @param original Original image
     */
    public static void paintTextBlocks(List<Rect> textBlocks, Mat original)
    {
        for (Rect r : textBlocks)
        {
            Imgproc.rectangle(original, new Point(r.x,r.y), new Point(r.x+r.width,r.y+r.height),
                    new Scalar(255.0),2);
        }
    }

    /**
     * Applies the k-means clustering algorithm, using only 2 clusters,
     * in order to achieve image thresholding
     * @param image The target Mat image
     * @return The original image converted to binary (black and white)
     */
    public static Mat thresholdImageWithKmeans(Mat image)
    {
        Mat data = new Mat(image.height() * image.width(), 1, CvType.CV_32FC1);
        int k = 0;
        for (int i = 0; i < image.height(); i++) {
            for (int j = 0; j < image.width(); j++) {
                data.put(k, 0, image.get(i, j)[0]);
                k++;
            }
        }
        int clusters = 2;
        Mat labels = new Mat();

        /*
         TermCriteria Constructor Parameters:
         type: The type of termination criteria, one of TermCriteria::Type
         maxCount: The maximum number of iterations or elements to compute.
         epsilon: The desired accuracy or change in parameters at which the iterative algorithm stops.
        */
        TermCriteria criteria = new TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 10, 1);
        int attempts = 5;
        int flag = Core.KMEANS_PP_CENTERS;
        Mat centers = new Mat();
        Core.kmeans(data, clusters, labels, criteria, attempts, flag, centers);
        return MatProcessor.convertLabelsToBinary(labels, image);
    }

    /**
     * Converts the k-means labels result into a new binary image
     * @param labels The labels result from the kmeans algorithm
     * @param image The Mat image for which we calculated the kmeans thresholding
     */
    public static Mat convertLabelsToBinary(Mat labels, Mat image)
    {
        Mat binary = new Mat(image.height(), image.width(), CvType.CV_8UC1);
        int k=0;
        for (int i=0; i<binary.height(); i++)
        {
            for (int j=0; j<binary.width(); j++)
            {
                if (Double.compare(labels.get(k,0)[0],1.0) == 0) {
                    binary.put(i,j,255.0);
                } else {
                    binary.put(i,j,0.0);
                }
                k++;
            }
        }
        return binary;
    }
}