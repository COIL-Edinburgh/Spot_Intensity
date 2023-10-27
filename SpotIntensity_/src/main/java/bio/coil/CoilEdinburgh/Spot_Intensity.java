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
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
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
	
	@Parameter(label = "Channel 1", choices = { "Blue", "Green", "Red", "NotUsed"}, autoFill = false)
	private String channelnumA = "Blue";
	
	@Parameter(label = "Channel 2", choices = { "Blue", "Green", "Red", "NotUsed"}, autoFill = false)
	private String channelnumB = "Green";
	
	@Parameter(label = "Channel 3", choices = { "Blue", "Green", "Red", "NotUsed"}, autoFill = false)
	private String channelnumC = "Red";
	
	//@Parameter(label = "Channel 4", choices = { "Blue", "Green", "Red", "NotUsed"}, autoFill = false)
	//private String channelnumD = "NotUsed";
	
	@Parameter(label = "Which colour channel has objects", autoFill = false)
	private String activeChannel = "Blue";
	
	//@Parameter(label = "Minimum Spot Size", autoFill = false)
	//private int activeMinSpotSize = 5;
	
	@Parameter(label = "Size of Spot", autoFill = false)
	private int activeMaxSpotSize = 6;
	
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
    Boolean firstRun;
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
   //     RandomAccessibleInterval<T> ChannelFour = null;
        if(channelnumA.equals("Blue")) {
        	ChannelOne = projectedImages.get(0);
        }
        if(channelnumA.equals("Green")) {
        	ChannelTwo = projectedImages.get(0);
        }
        if(channelnumA.equals("Red")) {
        	ChannelThree = projectedImages.get(0);
        }
   //     if(channelnumA.equals("NotUsed")) {
    //    	ChannelFour = projectedImages.get(0);
   //     }
        
        if(channelnumB.equals("Blue")) {
        	ChannelOne = projectedImages.get(1);
        }
        if(channelnumB.equals("Green")) {
        	ChannelTwo = projectedImages.get(1);
        }
        if(channelnumB.equals("Red")) {
        	ChannelThree = projectedImages.get(1);
        }
   //     if(channelnumB.equals("NotUsed")) {
   //     	ChannelFour = projectedImages.get(1);
   //     }
        
        if(channelnumC.equals("Blue")) {
        	ChannelOne = projectedImages.get(2);
        }
        if(channelnumC.equals("Green")) {
        	ChannelTwo = projectedImages.get(2);
        }
        if(channelnumC.equals("Red")) {
        	ChannelThree = projectedImages.get(2);
        }
 //       if(channelnumC.equals("NotUsed")) {
  //      	ChannelFour = projectedImages.get(2);
   //     }
  /*      
        if(channelnumD.equals("Blue")) {
        	ChannelOne = projectedImages.get(3);
        }
        if(channelnumD.equals("Green")) {
        	ChannelTwo = projectedImages.get(3);
        }
        if(channelnumD.equals("Red")) {
        	ChannelThree = projectedImages.get(3);
        }
        if(channelnumD.equals("NotUsed")) {
        	ChannelFour = projectedImages.get(3);
        }
        
     */
        firstRun =true;
        labelImage = ImageJFunctions.wrap((RandomAccessibleInterval<T>) ChannelTwo,"Labelled Green Image");
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
        IJ.run(labelImage, "Enhance Contrast", "saturated=0.35"); //Autoscale image
        String name = file.getAbsolutePath();
		String newName = name.substring(0, name.indexOf("."));	
		String imageName = newName + ".jpg";
        IJ.saveAs(labelImage, "Jpeg", imageName);
        new WaitForUserDialog("Finished", "Plugin Finished").show();
    }
    
    /*
     * Function to split the 3 channel image into its
     * component colour channels
     */
    public List<RandomAccessibleInterval<T>> SplitChannels(Img<T> image){
    	List<RandomAccessibleInterval<T>> theChannels =  new ArrayList<>();
    	
    	// defining which dimension of the image represents channels
   	   	int channelDim = 3;
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
    		
    		IterableInterval<T> theGreenChannel = (IterableInterval<T>) ChannelTwo;
           	Roi roi = rm.getRoi(roiNum.get(x)-1);
        	rm.select(roiNum.get(x)-1);
        	mask = cs.convert(roi, RealMask.class);
 
        	// Convert ROI from R^n to Z^n.
        	RandomAccessibleOnRealRandomAccessible<BoolType> discreteROI = Views.raster(Masks.toRealRandomAccessible(mask));
        	// Apply finite bounds to the discrete ROI.

      
        	IntervalView<BoolType> boundedDiscreteROIGreen = Views.interval(discreteROI, theGreenChannel);
        	// Create an iterable version of the finite discrete ROI.
        	IterableRegion<BoolType> iterableROIGreen = Regions.iterable(boundedDiscreteROIGreen);
        	
        
        	IterableInterval<T> thegfpResultingPixels = Regions.sample(iterableROIGreen, (RandomAccessible<T>) theGreenChannel);  //Pixels from green channel
    		CheckForSpots(thegfpResultingPixels, ChannelTwo, ChannelThree, x);
        	
    	}
    }
    
    private void CheckForSpots(IterableInterval<T> thegfpResultingPixels, RandomAccessibleInterval<T> ChannelTwo, RandomAccessibleInterval<T> ChannelThree, int x ) {
    	
   
    	double highestValue=0;
    
    	
    	//Assign cursor to iterate through the pixels within ROI
	
		Cursor<T> cursorGFP = thegfpResultingPixels.localizingCursor();
	
		cursorGFP.fwd();
		double [] Positions = new double[2];
		//Find Brightest Pixel
		for (int y=0;y<thegfpResultingPixels.size();y++) {
	
			cursorGFP.fwd();
		
			if(cursorGFP.get().getRealDouble()>highestValue) {
				highestValue = cursorGFP.get().getRealDouble();
				if (Positions.length>1) {
					Positions = new double[2];
				}
				Positions = cursorGFP.positionAsDoubleArray(); //Read xy coordinates of pixel
			}
		}
		//MeasureGreen
		ImagePlus impGreen = ImageJFunctions.wrap(ChannelTwo,"Green");
		impGreen.show();
		IJ.run(impGreen, "Enhance Contrast", "saturated=0.35");
		int xPos = (int) Positions[0]-(activeMaxSpotSize/3);
		int yPos = (int) Positions[1]-(activeMaxSpotSize/3);
		impGreen.setRoi(xPos, yPos, activeMaxSpotSize, activeMaxSpotSize);
		IJ.run("Set Measurements...", "area mean standard modal min centroid median redirect=None decimal=2");
		IJ.run(impGreen, "Measure", "");
		ResultsTable rt = new ResultsTable();	
		rt = Analyzer.getResultsTable();
		double spotMeanG = rt.getValueAsDouble(1, 0);
		double spotModeG = rt.getValueAsDouble(3, 0);
		double spotMinG = rt.getValueAsDouble(4, 0);
		double spotMaxG = rt.getValueAsDouble(5, 0);
		double spotMedianG = rt.getValueAsDouble(21, 0);
		ClearResults();
		
		//MeasureRed
		ImagePlus impRed = ImageJFunctions.wrap(ChannelThree,"Red");
		impRed.show();
		IJ.run(impRed, "Enhance Contrast", "saturated=0.35");
		xPos = (int) Positions[0]-(activeMaxSpotSize/3);
		yPos = (int) Positions[1]-(activeMaxSpotSize/3);
		impRed.setRoi(xPos, yPos, activeMaxSpotSize, activeMaxSpotSize);
		IJ.run("Set Measurements...", "area mean standard modal min centroid median redirect=None decimal=2");
		IJ.run(impRed, "Measure", "");
		rt = Analyzer.getResultsTable();
		double spotMeanR = rt.getValueAsDouble(1, 0);
		double spotModeR = rt.getValueAsDouble(3, 0);
		double spotMinR = rt.getValueAsDouble(4, 0);
		double spotMaxR = rt.getValueAsDouble(5, 0);
		double spotMedianR = rt.getValueAsDouble(21, 0);
		
	
		ClearResults();
		
		OutputResults(spotMeanG,spotModeG,spotMinG,spotMaxG,spotMedianG,spotMeanR,spotModeR,spotMinR,spotMaxR,spotMedianR,x);
		
		//Label the Green Image
    	ImageProcessor ip = labelImage.getProcessor();
		Font font = new Font("SansSerif", Font.PLAIN, 18);
		ip.setFont(font);
		ip.setColor(new Color(1, 1, 1));
    	String cellNumber = Integer.toString(x+1);
    	ip.drawString(cellNumber, xPos+6, yPos+6);
		labelImage.updateAndDraw();
		

		impGreen.changes=false;
		impGreen.close();
		impRed.changes=false;
		impRed.close();
    }
    
 
    public void getRegionValues(int xPos, int yPos ,int x) {
   
    		for (int a=xPos-3; a<xPos+3;a++) {
    			for(int b=yPos-3; b<yPos+3;b++) {
    				
    			}
    		}
    	
    	
    	
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
    
    public void OutputResults(double spotMeanG,double spotModeG,double spotMinG, double spotMaxG, double spotMedianG, double spotMeanR, double spotModeR, double spotMinR, double spotMaxR, double spotMedianR, int x) {
    	String name = file.getAbsolutePath();
		String newName = name.substring(0, name.indexOf("."));	
		String CreateName = newName + ".txt";
		String FILE_NAME = CreateName;
		
		try{
			FileWriter fileWriter = new FileWriter(FILE_NAME,true);
			BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
	
			if(x==0 && firstRun==true) {
				bufferedWriter.newLine();
				bufferedWriter.write(" File= " + name);
				bufferedWriter.newLine();
				firstRun = false;
			}			
			bufferedWriter.write("Cell " + (x+1) + " Green Mean = " + spotMeanG + " Green Mode = "  + spotModeG + " Green Min = " + spotMinG + " Green Max = " + spotMaxG + " Green Median = " + spotMedianG + " Red Mean = " + spotMeanR + " Red Mode = " + spotModeR + " Red Min = " + spotMinR + " Red Max = " + spotMaxR + " Red Median = " + spotMedianR);		
			bufferedWriter.newLine();
			bufferedWriter.close();

		}
		catch(IOException ex) {
			System.out.println(
            "Error writing to file '"
            + FILE_NAME + "'");
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
    
    public void ClearResults(){
		
		ResultsTable emptyrt = new ResultsTable();	
		emptyrt = Analyzer.getResultsTable();
		int valnums = emptyrt.getCounter();
		for(int a=0;a<valnums;a++){
			IJ.deleteRows(0, a);
		}
	}


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
