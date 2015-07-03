package polenFeatureSimilarity;

import georegression.struct.point.Point2D_F64;
import ij.IJ;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import configuration.Configuration;
import surfExtractor.image_set.Image;
import surfExtractor.image_set.ImageClass;
import surfExtractor.image_set.ImageSet;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.core.image.ConvertBufferedImage;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.feature.SurfFeature;
import boofcv.struct.image.ImageFloat32;

public class PolenFeatureSimilarity {

	public static void main(String[] args) {
		Configuration config = new Configuration();
		
		config.addNewValidParameter("image.to.analyse");
		config.addNewValidParameter("imageset.location");

		config.readFromRunArgs(args);

		config.debugParameters();

		try {
			config.verifyArgs();
		} catch (Exception e) {
			e.printStackTrace();
		}

		new PolenFeatureSimilarity(config);
	}

	public PolenFeatureSimilarity(Configuration config) {
		ImageFloat32 imageToAnalyse = UtilImageIO.loadImage(config.getConfiguration("image.to.analyse"), ImageFloat32.class);
		BufferedImage imageBuf = null;
		if(imageToAnalyse == null) {
			imageBuf = IJ.openImage(config.getConfiguration("image.to.analyse")).getBufferedImage();

			imageToAnalyse = ConvertBufferedImage.convertFrom(imageBuf, imageToAnalyse);
		}
		ImageSet imageSet = null;
		try {
			imageSet = new ImageSet(config.getConfiguration("imageset.location"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		ArrayList<SurfFeature> knownFeatures = getFeaturesFromImageSet(imageSet);

		DetectDescribePoint<ImageFloat32, SurfFeature> analyseImageSurf = FactoryDetectDescribe.surfStable(new ConfigFastHessian(0, 2, 200, 2, 9, 4, 4), null, null, ImageFloat32.class);
		analyseImageSurf.detect(imageToAnalyse);

		BufferedImage avgResult = new BufferedImage(imageToAnalyse.getWidth(), imageToAnalyse.getHeight(), BufferedImage.TYPE_INT_RGB);
		BufferedImage minResult = new BufferedImage(imageToAnalyse.getWidth(), imageToAnalyse.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics minG = minResult.getGraphics();
		Graphics avgG = avgResult.getGraphics();

		minG.setColor(new Color(127, 127, 127));
		avgG.setColor(new Color(127, 127, 127));

		minG.fillRect(0, 0, minResult.getWidth(), minResult.getHeight());
		avgG.fillRect(0, 0, avgResult.getWidth(), avgResult.getHeight());

		double maxAvg = 0;
		double maxMin = 0;
		double minAvg = 999;
		double minMin = 999;
		double avgAvg = 0;
		double avgMin = 0;
		int avgCount = 0;
		int minCount = 0;
		
		System.out.println("Num of features: " + knownFeatures.size());

		for (int i = 0; i < analyseImageSurf.getNumberOfFeatures(); i++) {
			double min = getMinimumSimilarity(analyseImageSurf.getDescription(i).getValue(), knownFeatures);
			double avg = getAvgSimilarity(analyseImageSurf.getDescription(i).getValue(), knownFeatures);

			if (min > maxMin)
				maxMin = min;
			if (avg > maxAvg)
				maxAvg = avg;
			if(avg < minAvg) 
				minAvg = avg;
			if(min < minMin) 
				minMin = min;
			
			avgAvg += avg;
			avgMin += min;
			avgCount++;
			minCount++;
		}
		

		for (int i = 0; i < analyseImageSurf.getNumberOfFeatures(); i++) {
			SurfFeature theFeat = analyseImageSurf.getDescription(i);
			Point2D_F64 loc = analyseImageSurf.getLocation(i);
			double min = getMinimumSimilarity(analyseImageSurf.getDescription(i).getValue(), knownFeatures);
			double avg = getAvgSimilarity(analyseImageSurf.getDescription(i).getValue(), knownFeatures);

			min = (min - minMin) / (maxMin - minMin);
			avg = (avg - minAvg) / (maxAvg - minAvg);
			//min /= maxMin;
			//avg /= maxAvg;

			minG.setColor(new Color((int) (255 * min), (int) (255 * min), (int) (255 * min)));
			avgG.setColor(new Color((int) (255 * avg), (int) (255 * avg), (int) (255 * avg)));

			//System.out.println("vals : " + min + " _ " + avg);

			int size = 6;
			minG.fillRect((int) (loc.getX() - size / 2), (int) loc.getY(), size, size);
			avgG.fillRect((int) (loc.getX() - size / 2), (int) loc.getY(), size, size);
		}

		try {
			ImageIO.write(minResult, "png", new File("c:/minResult.png"));
			ImageIO.write(avgResult, "png", new File("c:/avgResult.png"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("done boyz");
		
		System.out.println("Max min: " + maxMin);
		System.out.println("Min min: " + minMin);
		System.out.println("Avg min: " + avgMin / minCount);
		System.out.println("Max avg: " + maxAvg);
		System.out.println("Min avg: " + minAvg);
		System.out.println("Avg avg: " + avgAvg / avgCount);

	}

	public ArrayList<SurfFeature> getFeaturesFromImageSet(ImageSet is) {
		ArrayList<SurfFeature> features = new ArrayList<SurfFeature>();
		DetectDescribePoint<ImageFloat32, SurfFeature> surf = FactoryDetectDescribe.surfStable(new ConfigFastHessian(0, 2, 200, 2, 9, 4, 4), null, null, ImageFloat32.class);

		for (ImageClass ic : is.getImageClasses()) {
			for (Image image : ic.getImages()) {
				ImageFloat32 loadedImage = UtilImageIO.loadImage(image.getFile().getAbsolutePath(), ImageFloat32.class);
				if (loadedImage == null) {
					BufferedImage imageBuf;
					imageBuf = IJ.openImage(image.getFile().getAbsolutePath()).getBufferedImage();
					loadedImage = ConvertBufferedImage.convertFrom(imageBuf, loadedImage);
				}
				surf.detect(loadedImage);
				for (int i = 0; i < surf.getNumberOfFeatures(); i++) {
					features.add(surf.getDescription(i));
				}
			}
		}
		return features;
	}

	public double getMinimumSimilarity(double[] theFeat, ArrayList<SurfFeature> known) {
		double minimum = getDistance(theFeat, known.get(0).getValue());
		for (int i = 0; i < known.size(); i++) {
			double cal = getDistance(theFeat, known.get(i).getValue());

			if (minimum > cal)
				minimum = cal;
		}
		return minimum;
	}

	public double getAvgSimilarity(double[] theFeat, ArrayList<SurfFeature> known) {
		double sum = 0;
		int count = 0;
		for (int i = 0; i < known.size(); i++) {
			sum += getDistance(theFeat, known.get(i).getValue());
			count++;
		}
		return sum / count;
	}

	public double getDistance(double[] a, double[] b) {
		double sum = 0;
		for (int i = 0; i < a.length; i++) {
			sum += sqr(a[i] - b[i]);
		}
		return Math.sqrt(sum);
	}

	public double sqr(double a) {
		return a * a;
	}
}
