/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package bio.coil.CoilEdinburgh;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.display.ImageDisplayService;
import net.imagej.display.WindowService;
import net.imagej.ops.Op;
import net.imagej.ops.OpService;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.Masks;
import net.imglib2.roi.RealMask;
import net.imglib2.roi.Regions;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible;
import net.imglib2.view.Views;

import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.display.DisplayService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import ij.IJ;

import ij.ImagePlus;

import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;


import javax.swing.JOptionPane;

/**
 
 */
@Plugin(type = Command.class, menuPath = "Plugins>Spot_Intensity")
public class Spot_Intensity<T extends RealType<T>> implements Command {
	@Parameter
	File file;
	
	@Parameter(label = "Channel 1", choices = { "Blue", "Green", "Red", "Absent"}, autoFill = false)
	private String channelnumA = "Blue";
	
	@Parameter(label = "Channel 2", choices = { "Blue", "Green", "Red", "Absent"}, autoFill = false)
	private String channelnumB = "Green";
	
	@Parameter(label = "Channel 3", choices = { "Blue", "Green", "Red", "Absent"}, autoFill = false)
	private String channelnumC = "Red";
	
	@Parameter(label = "Which colour channel has objects", autoFill = false)
	private String activeChannel = "Blue";
	
	@Parameter(label = "Minimum Spot Size", autoFill = false)
	private int activeMinSpotSize = 5;
	
	@Parameter(label = "MaximumSpot Size", autoFill = false)
	private int activeMaxSpotSize = 250;
	
	@Parameter
	private DisplayService impDisplay;

    @Parameter
    private UIService uiService;

    @Parameter
    private OpService opService;
    
    @Parameter
    static ImageJ ij;
    
    @Parameter
    ImageDisplayService imDispService;
    
    @Parameter
    WindowService winService;
    
    @Parameter
    private ConvertService cs;
    
    int x;
    int y;
    ImagePlus labelImage;

    RoiManager rm;
    
    @SuppressWarnings("unchecked")
	@Override
    public void run() {
    	
    	Dataset currentData = null;	
    	try {
			currentData = ij.scifio().datasetIO().open(file.getPath());
		} catch (IOException e) {
			e.printStackTrace();
		}

		Img<T> image = (Img<T>) currentData.getImgPlus();  
		
        //Split the Channels
        List<RandomAccessibleInterval<T>> theChannels = new ArrayList<>();
        long numChannels = image.dimension(2);
        if (numChannels>1){
        	theChannels = SplitChannels(image);
        }
        
        List <RandomAccessibleInterval<T>> projectedImages =   MaxProject(theChannels);
        
        RandomAccessibleInterval<T> ChannelOne = null;
        RandomAccessibleInterval<T> ChannelTwo = null;
        RandomAccessibleInterval<T> ChannelThree = null;
        if(channelnumA.equals("Blue")) {
        	ChannelOne = projectedImages.get(0);
        }
        if(channelnumA.equals("Green")) {
        	ChannelTwo = projectedImages.get(0);
        }
        if(channelnumA.equals("Red")) {
        	ChannelThree = projectedImages.get(0);
        }
        
        if(channelnumB.equals("Blue")) {
        	ChannelOne = projectedImages.get(1);
        }
        if(channelnumB.equals("Green")) {
        	ChannelTwo = projectedImages.get(1);
        }
        if(channelnumB.equals("Red")) {
        	ChannelThree = projectedImages.get(1);
        }
        
        if(channelnumC.equals("Blue")) {
        	ChannelOne = projectedImages.get(2);
        }
        if(channelnumC.equals("Green")) {
        	ChannelTwo = projectedImages.get(2);
        }
        if(channelnumC.equals("Red")) {
        	ChannelThree = projectedImages.get(2);
        }   
        
        labelImage = ImageJFunctions.wrap((RandomAccessibleInterval<T>) ChannelOne,"Labelled Dapi");
		labelImage.show();
		
        RandomAccessibleInterval<T> nucMask = FindNuclei(ChannelOne);
        rm = getRois(nucMask);
        
        ArrayList<Integer> roiNum = new ArrayList<Integer>();
        try {
			roiNum = selectRoi(ChannelOne,ChannelTwo,ChannelThree);
		} catch (InterruptedException e) {
			
			e.printStackTrace();
		}
        
        MeasureSelectedRegions(roiNum,ChannelTwo,ChannelOne, ChannelThree, rm);
        new WaitForUserDialog("Finished", "Plugin Finished").show();
    }
    
    /*
     * Function to split the 3 channel image into its
     * component colour channels
     */
    public List<RandomAccessibleInterval<T>> SplitChannels(Img<T> image){
    	List<RandomAccessibleInterval<T>> theChannels =  new ArrayList<>();
    	
    	// defining which dimension of the image represents channels
   	   	int channelDim = 2;
    	// how many channels do we have?
   	   	long numChannels = image.dimension(channelDim);
   	   	
   	   	for (int channelIndex = 0; channelIndex < numChannels; channelIndex++) {
   	   	RandomAccessibleInterval<T> inputChannel = (RandomAccessibleInterval<T>) ij.op().transform().hyperSliceView(image, channelDim, channelIndex);
		   	theChannels.add(inputChannel);
   	   	}
    	
    	return theChannels;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
	public List MaxProject(List<RandomAccessibleInterval<T>> theChannels) {
		
    	List <RandomAccessibleInterval<T>> projectedImages = new ArrayList<>();
    	
    	for(int x=0; x<theChannels.size();x++) {
    		
    		RandomAccessibleInterval<T> tempImage = theChannels.get(x);
    		//Gets the XY dimensions of the image
    		FinalInterval interval = new FinalInterval(tempImage.dimension(0), tempImage.dimension(1));
    			  			
    		//Creates a single plane image blank image to hold the result
    		//Img<T> projected = (Img<T>) ij.op().create().img(interval);
    		RandomAccessibleInterval<T> projected = (RandomAccessibleInterval<T>) ij.op().create().img(interval);
    		
    		//Calculates max values for all pixels in z stack
    		Op statsOp = ij.op().op("stats.max", tempImage);
    			
    		//Selects the dimension holding the z info		
    		int projectDim = 2;
    			
    		//projects the original image and stores it in the newly created blank image		
    		ij.op().run("project", projected, tempImage, statsOp, projectDim);	
    	
    		projectedImages.add(projected);
    	
    	}
    	
    	
    	return projectedImages;
    	
    }
    
    @SuppressWarnings("unchecked")
	public RandomAccessibleInterval<T> FindNuclei(RandomAccessibleInterval<T>ChannelOne){
		
    	RandomAccessibleInterval<T> nucMask = null;
    	
    	uiService.show(ChannelOne);
    	 IJ.run("Cellpose Advanced", "diameter=" + 80 + " cellproba_threshold=" + 0.0 + " flow_threshold=" + 0.4
                + " anisotropy=" + 1.0 + " diam_threshold=" + 12.0 + " model=" + "nuclei" + " nuclei_channel=" + 0
                + " cyto_channel=" + 1 + " dimensionmode=" + "2D" + " stitch_threshold=" + -1 + " omni=" + false
                + " cluster=" + false + " additional_flags=" + "");
    	
    	//ImagePlus mask = WindowManager.getCurrentImage();
    	Dataset image = ij.imageDisplay().getActiveDataset();
    	nucMask = (RandomAccessibleInterval<T>) image;
    	return nucMask;
    	
    }
    
    private RoiManager getRois(RandomAccessibleInterval<T> nucMask) {
    	
    	ImagePlus mask = ImageJFunctions.wrap(nucMask,""); 
    	ImageStatistics stats = mask.getStatistics();
    	
    	for (int i = 1; i < stats.max + 1; i++) {
            //Set the threshold for the cell and use analyse particles to add to ROI manager
            IJ.setThreshold(mask, i, i);
            IJ.run(mask, "Analyze Particles...", "add");
        }
    	rm = RoiManager.getInstance();
    	mask.changes=false;
    	mask.close();
    	return rm;
    }
    
    private ArrayList<Integer> selectRoi(RandomAccessibleInterval<T>ChannelOne, RandomAccessibleInterval<T>ChannelTwo, RandomAccessibleInterval<T>ChannelThree) throws InterruptedException {
    	
    	RoiManager roiManager = new RoiManager();
    	roiManager = RoiManager.getInstance();
        Roi[] outlines = roiManager.getRoisAsArray();
        ArrayList<Integer> roiNum = new ArrayList<Integer>();
        ImagePlus imp = ImageJFunctions.wrap(ChannelOne,"");
        imp.show();
        IJ.run(imp, "Enhance Contrast", "saturated=0.35");  
        IJ.run("Out [-]", "");
        ImagePlus impTwo = ImageJFunctions.wrap(ChannelTwo,"");
        impTwo.show();
        IJ.run(impTwo, "Enhance Contrast", "saturated=0.35"); 
        IJ.run("Out [-]", "");
        ImagePlus impThree = ImageJFunctions.wrap(ChannelThree,"");
        impThree.show();
        IJ.run(impThree, "Enhance Contrast", "saturated=0.35"); 
        IJ.run("Out [-]", "");
        
        new WaitForUserDialog("Arrange Images", "Place the images so that you can see all channels").show();
        
        String answer = "y";
        do {
        	imp.getCanvas().addMouseListener(mouseListener);
            double scale = imp.getCanvas().getMagnification();
        	new WaitForUserDialog("Click", "Click a nucleus(ONLY CLICK DAPI IMAGE) then OK").show();
        	x= (int) (x/scale);
    		y= (int) (y/scale);
    		int count=1;
    		for (Roi roi : outlines) {
    			if (roi.contains(x, y)) {
    				roiNum.add(count);
    				imp.getCanvas().removeMouseListener(mouseListener);
    				break;
    			}
    			count++;
    		}
    		imp.getCanvas().removeMouseListener(mouseListener);
    		answer = JOptionPane.showInputDialog("Do you want to select another nuclei y/n ");
        }while(answer.equals("y")); 
		imp.changes=false;
		imp.close();
		impTwo.changes=false;
		impTwo.close();
		impThree.changes=false;
		impThree.close();
 		return roiNum;
        
    }
    
    @SuppressWarnings("unchecked")
	private void  MeasureSelectedRegions(ArrayList<Integer>roiNum, RandomAccessibleInterval<T> ChannelTwo, RandomAccessibleInterval<T> ChannelOne, RandomAccessibleInterval<T>ChannelThree, RoiManager rm) {
    	RealMask mask = null;
    	
    	//Loop through all the split regions of interest found from the GFP channel
    	for (int x=0;x<roiNum.size();x++) {
    		
    	//	IterableInterval<T> theBlueChannel = (IterableInterval<T>) ChannelOne;
    		IterableInterval<T> theRedChannel = (IterableInterval<T>) ChannelThree;
    		IterableInterval<T> theGreenChannel = (IterableInterval<T>) ChannelTwo;
           	Roi roi = rm.getRoi(roiNum.get(x)-1);
        	rm.select(roiNum.get(x)-1);
        	mask = cs.convert(roi, RealMask.class);
 
        	// Convert ROI from R^n to Z^n.
        	RandomAccessibleOnRealRandomAccessible<BoolType> discreteROI = Views.raster(Masks.toRealRandomAccessible(mask));
        	// Apply finite bounds to the discrete ROI.

        	IntervalView<BoolType> boundedDiscreteROIRed = Views.interval(discreteROI, theRedChannel);
        	IntervalView<BoolType> boundedDiscreteROIGreen = Views.interval(discreteROI, theGreenChannel);
        	// Create an iterable version of the finite discrete ROI.
        	IterableRegion<BoolType> iterableROIRed = Regions.iterable(boundedDiscreteROIRed);
        	IterableRegion<BoolType> iterableROIGreen = Regions.iterable(boundedDiscreteROIGreen);
        	
        	IterableInterval<T> therfpResultingPixels = Regions.sample(iterableROIRed, (RandomAccessible<T>) theRedChannel); //Pixels from red channel
        	IterableInterval<T> thegfpResultingPixels = Regions.sample(iterableROIGreen, (RandomAccessible<T>) theGreenChannel);  //Pixels from green channel
        	Histogram1d<T> histogram = ij.op().image().histogram(thegfpResultingPixels);
       		CheckForSpots(histogram, therfpResultingPixels, thegfpResultingPixels, theRedChannel, theGreenChannel, x);
    	}
    }
    
    private void CheckForSpots(Histogram1d<T>histogram, IterableInterval<T> therfpResultingPixels, IterableInterval<T> thegfpResultingPixels, IterableInterval<T>theRedChannel, IterableInterval<T>theGreenChannel, int x ) {
		
    	uiService.show(theGreenChannel);
    	T thresholdValue = opService.threshold().otsu(histogram);
    	double threshLimit = thresholdValue.getRealDouble()+200;
    	ArrayList<double[]> rankedRedPixelsPosition = new ArrayList<double[]>();
    	ArrayList<Double> redpixelIntensity = new ArrayList<>();
    	ArrayList<double[]> rankedGreenPixelsPosition = new ArrayList<double[]>();
    	ArrayList<Double> greenpixelIntensity = new ArrayList<>();
    	
    	//Assign cursor to iterate through the pixels within ROI
		Cursor<T> cursorRFP = therfpResultingPixels.localizingCursor();
		Cursor<T> cursorGFP = thegfpResultingPixels.localizingCursor();
		cursorRFP.fwd();
		cursorGFP.fwd();
		
		for (int y=0;y<thegfpResultingPixels.size();y++) {
			cursorRFP.fwd();
			cursorGFP.fwd();
			if(cursorGFP.get().getRealDouble()>threshLimit) {
				double [] Positions = cursorGFP.positionAsDoubleArray(); //Read xy coordinates of pixel
				redpixelIntensity.add(cursorRFP.get().getRealDouble());
				rankedRedPixelsPosition.add(Positions);
				greenpixelIntensity.add(cursorGFP.get().getRealDouble());
				rankedGreenPixelsPosition.add(Positions);
			}
		}
		
	
		nearestNeighbours(rankedRedPixelsPosition, rankedGreenPixelsPosition, redpixelIntensity, greenpixelIntensity, x);
		
    }
    
    @SuppressWarnings("unchecked")
	public void nearestNeighbours(ArrayList<double[]> rankedRfpPixelsPosition, ArrayList<double[]> rankedGfpPixelsPosition, ArrayList<Double>pixelRfpIntensity, ArrayList<Double>pixelGfpIntensity, int x) {	
    	int xPosition;
		int yPosition;
		int rfpIntensity;
		int gfpIntensity;
		ArrayList<ArrayList<int[]>> foundRfpRegions = new ArrayList<>();
		ArrayList<int[]> currentRfpRegion = new ArrayList<>();
		ArrayList<ArrayList<int[]>> foundGfpRegions = new ArrayList<>();
		ArrayList<int[]> currentGfpRegion = new ArrayList<>();
		
		List<Integer> usedCoordinates = new ArrayList<>();
		boolean exitLoop = false;
		int counter=0;
		do {
    	    double[] pos = rankedRfpPixelsPosition.get(counter);
    		xPosition = (int)pos[0];
    		yPosition = (int)pos[1];
    		rfpIntensity = (int) Math.round(pixelRfpIntensity.get(counter));
    		gfpIntensity = (int) Math.round(pixelGfpIntensity.get(counter));
    		
    		if(counter<1) {
    			currentRfpRegion.add(new int[] {xPosition,yPosition, rfpIntensity});
    			currentGfpRegion.add(new int[] {xPosition,yPosition, gfpIntensity});  			
    			usedCoordinates.add(counter);
    		}
    	
    		//Search currentRegion for close pixels
    		for (int a=0;a<currentRfpRegion.size();a++) {
    			int X = currentRfpRegion.get(a)[0];
    			int Y = currentRfpRegion.get(a)[1];
    			if ((X==(xPosition-1) || X==(xPosition+1) || X==(xPosition)) && (Y==(yPosition-1) || Y==(yPosition+1) || Y==(yPosition))) {
        			
    				//Check to make sure it hasn't already been used and add to list
    				if (usedCoordinates.contains(counter)==false) {
    					currentRfpRegion.add(new int [] {xPosition, yPosition, rfpIntensity});
    					currentGfpRegion.add(new int [] {xPosition, yPosition, gfpIntensity});					
    					usedCoordinates.add(counter);
    					break;
    				}
    		}
    		}
    		if (counter==rankedRfpPixelsPosition.size()-1) {
    			//Clone current region into the found regions array
    			foundRfpRegions.add((ArrayList<int[]>)currentRfpRegion.clone());
    			foundGfpRegions.add((ArrayList<int[]>)currentGfpRegion.clone()); 		
  
    			//Add points to labelled image
    			ImageProcessor labelProcessor = labelImage.getProcessor();  
    			for (int a=0;a<foundGfpRegions.size();a++) {
    				ArrayList<int[]> roiGFPSpot = foundRfpRegions.get(a);
    				for(int b=0;b<roiGFPSpot.size();b++) {
    					int[]  Pos = roiGFPSpot.get(b);
    					labelProcessor.set(Pos[0], Pos[1], 254);
    				}
    			}
    						
    			
    			
    			//Clear the current region
    	    	currentRfpRegion.clear();
    	    	currentGfpRegion.clear(); 
   
    	    	deleteUsedCoordinates(usedCoordinates,rankedRfpPixelsPosition,rankedGfpPixelsPosition);
    	    	usedCoordinates.clear();
    	    	//reset counter for next ROI, move it down a negative
    	    	//number for each new group so that each roi gets a
    	    	//separate number
    	    	counter = -1;
    		}

    		counter++;
    		if (rankedRfpPixelsPosition.size()<1) {
    			exitLoop=true;
    		}
		}while(exitLoop==false);
    	
	
		
    	OutputResults(foundRfpRegions, foundGfpRegions);
    	
    	//Label the Brightfield Image
    	ImageProcessor ip = labelImage.getProcessor();
		Font font = new Font("SansSerif", Font.PLAIN, 18);
		ip.setFont(font);
		ip.setColor(new Color(255, 255, 255));
    	String cellNumber = Integer.toString(x+1);
    	ip.drawString(cellNumber, xPosition, yPosition);
		labelImage.updateAndDraw();
   
    }
    
    
    public void deleteUsedCoordinates(List<Integer>usedCoordinates, ArrayList<double[]>rankedRfpPixelsPosition, ArrayList<double[]>rankedGfpPixelsPosition) {
    	ArrayList<double[]>deleteTheseCoordinates=new ArrayList<>();
    	
    	for(int a=0;a<usedCoordinates.size();a++) {
    		double[] coordinatesToKill = rankedRfpPixelsPosition.get(usedCoordinates.get(a));
    		deleteTheseCoordinates.add(coordinatesToKill);
    	}
    	
    	for (int b=0;b<deleteTheseCoordinates.size();b++) {
			int X = (int) deleteTheseCoordinates.get(b)[0];
			int Y = (int) deleteTheseCoordinates.get(b)[1];
			for(int c=0;c<rankedRfpPixelsPosition.size();c++) {
				double[] pos = rankedRfpPixelsPosition.get(c);
	    		int xPosition = (int)pos[0];
	    		int yPosition = (int)pos[1];
	    		if (X==xPosition && Y==yPosition) {
	    			rankedRfpPixelsPosition.remove(c);
	    			rankedGfpPixelsPosition.remove(c);
	    		}
			}
    	}   	
    }
    
    
    public void OutputResults(ArrayList<ArrayList<int[]>>foundRfpRegions, ArrayList<ArrayList<int[]>>foundGfpRegions){
    	
    	
    	
    	for (int a=0;a<foundRfpRegions.size();a++) {
    		
    		ArrayList<int[]> roiRFPSpot = foundRfpRegions.get(a);
    		ArrayList<int[]> roiGFPSpot = foundGfpRegions.get(a);
    		double totalRFPIntensity = 0;
    		double meanRFPIntensity = 0;
    		double minRFPIntensity = 65000;
    		double maxRFPIntensity = 0;
    		double totalGFPIntensity = 0;
    		double meanGFPIntensity = 0;
    		double minGFPIntensity = 65000;
    		double maxGFPIntensity = 0;
    		double pixelArea = 0;
    		
    		if ((roiRFPSpot.size()>activeMinSpotSize) && (roiRFPSpot.size()<activeMaxSpotSize)) {		
    			for (int b=0; b<roiRFPSpot.size();b++) {
    				 int[] RfpValues = roiRFPSpot.get(b);
    				int[] GfpValues = roiGFPSpot.get(b);	
    				int spotRfpValues = RfpValues [2];
    				int spotGfpValues = GfpValues [2];
    				
    				totalRFPIntensity += spotRfpValues;
    				totalGFPIntensity += spotGfpValues;
    				
    				if (spotGfpValues<minGFPIntensity) {
    					minGFPIntensity = spotGfpValues;
    				}
    				if (spotGfpValues>maxGFPIntensity) {
    					maxGFPIntensity = spotGfpValues;
    				}
    				if (spotRfpValues<minRFPIntensity) {
    					minRFPIntensity = spotRfpValues;
    				}
    				if (spotRfpValues>maxRFPIntensity) {
    					maxRFPIntensity = spotRfpValues;
    				}
    				
    			}
    			pixelArea = roiRFPSpot.size();
    			meanGFPIntensity = totalGFPIntensity/roiRFPSpot.size();
    			meanRFPIntensity = totalRFPIntensity/roiRFPSpot.size();
    			
    		
    		}
  		
    		if ((roiRFPSpot.size()>activeMinSpotSize) && (roiRFPSpot.size()<activeMaxSpotSize)) {	
    			String name = file.getAbsolutePath();
    			String newName = name.substring(0, name.indexOf("."));	
    			String CreateName = newName + ".txt";
    			String FILE_NAME = CreateName;
    	
    			try{
    				FileWriter fileWriter = new FileWriter(FILE_NAME,true);
    				BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
    		
    				if(x==1) {
    					bufferedWriter.newLine();
    					bufferedWriter.write(" File= " + name);
    					bufferedWriter.newLine();
    				}			
    				bufferedWriter.write(" Area = " + pixelArea + " pixels " +" Total RFP Spot Intensity = " + totalRFPIntensity + " Total GFP Spot Intensity = " + totalGFPIntensity + " Mean RFP Spot Intensity = " + meanRFPIntensity + " Mean GFP Spot Intensity = " + meanGFPIntensity + " Min RFP Spot Intensity = " + minRFPIntensity + " Min GFP Spot Intensity = " + minGFPIntensity + " Max RFP Spot Intensity = " + maxRFPIntensity + " Max GFP Spot Intensity = " + maxGFPIntensity);		
    				bufferedWriter.newLine();
    				bufferedWriter.close();

    			}
    			catch(IOException ex) {
    				System.out.println(
                    "Error writing to file '"
                    + FILE_NAME + "'");
    			}
    		
    		}	
    	}
    	
    	
    }
    
    public MouseListener mouseListener = new MouseListener() {
    	@Override
    	public void mousePressed(MouseEvent e) {}
    	@Override
    	public void mouseReleased(MouseEvent e) {}
    	@Override
    	public void mouseExited(MouseEvent e) {}
    	@Override
    	public void mouseClicked(MouseEvent e) {
    		 x = e.getX();
    		 y = e.getY();
    		return;
    	}
    	@Override
    	public void mouseEntered(MouseEvent e) {}
   
    };


    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
    	
    	// create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        // invoke the plugin
        ij.command().run(Spot_Intensity.class, true);
    }

}
