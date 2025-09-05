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
import ij.gui.OvalRoi;
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

/*
 * Plugin uses Cellpose to find and threshold dapi labelled nuclei, the user then
 * clicks on a nucleus which contains a GFP positive spot, the brightest point of 
 * this spot is highlighted and measured. The same spot in the GFP channel is then
 * applied to other channels to measure the intensity. 
 */

@Plugin(type = Command.class, menuPath = "Plugins>Spot_Intensity")
public class Spot_Intensity<T extends RealType<T>> implements Command {
	@Parameter
	File file;
	
	@Parameter(label = "Channel 1", choices = { "Blue", "Green", "Red", "FarRed", "NotUsed"}, autoFill = false)
	private String channelnumA = "Blue";
	
	@Parameter(label = "Channel 2", choices = { "Blue", "Green", "Red", "FarRed", "NotUsed"}, autoFill = false)
	private String channelnumB = "Green";
	
	@Parameter(label = "Channel 3", choices = { "Blue", "Green", "Red", "FarRed", "NotUsed"}, autoFill = false)
	private String channelnumC = "Red";
	
	@Parameter(label = "Channel 4", choices = { "Blue", "Green", "Red", "FarRed", "NotUsed"}, autoFill = false)
	private String channelnumD = "FarRed";
	
	@Parameter(label = "Which colour channel has objects used to form the regions", choices = {"Blue", "Green", "Red", "FarRed"}, autoFill = false)
	private String activeChannel = "Blue";
	
	@Parameter(label = "Size of Spot", autoFill = false)
	private int activeMaxSpotSize = 6;
	@Parameter(label = "Environment Path: ", style = "directory")
    public File envpath;

    @Parameter(label = "Model Path: ")
    public File modelpath;

    @Parameter(label = "Cell Diameter: ")
    public int size;
	
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
    
    int cellNum;
  //  int x;
  //  int y;
    ImagePlus labelImage;
    Boolean firstRun;
    
    
    @SuppressWarnings("unchecked")
	@Override
    public void run() {
    	
    	//Set the measurements required for the plugin to work
    	IJ.run("Set Measurements...", "area mean standard modal min centroid center bounding fit shape feret's median redirect=None decimal=2");
    	
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
        RandomAccessibleInterval<T> ChannelFour = null;
        
        /*
         * ChannelOne always blue
         * ChannelTwo always green
         * ChannelThree always red
         * ChannelFour always farred
         */
        if (channelnumA!="NotUsed") {
        	if(channelnumA.equals("Blue")) {
        		ChannelOne = projectedImages.get(0);
        	}
        	if(channelnumA.equals("Green")) {
        		ChannelTwo = projectedImages.get(0);
        	}
        	if(channelnumA.equals("Red")) {
        		ChannelThree = projectedImages.get(0);
        	}
        	if(channelnumA.equals("FarRed")) {
        		ChannelFour = projectedImages.get(0);
        	}
        }
        
        if (channelnumB!="NotUsed") {
        	if(channelnumB.equals("Blue")) {
        		ChannelOne = projectedImages.get(1);
        	}
        	if(channelnumB.equals("Green")) {
        		ChannelTwo = projectedImages.get(1);
        	}
        	if(channelnumB.equals("Red")) {
        		ChannelThree = projectedImages.get(1);
        	}
        	if(channelnumB.equals("FarRed")) {
        		ChannelFour = projectedImages.get(1);
        	}
        }
        
        if (channelnumC!="NotUsed") {
        	if(channelnumC.equals("Blue")) {
        		ChannelOne = projectedImages.get(2);
        	}
        	if(channelnumC.equals("Green")) {
        		ChannelTwo = projectedImages.get(2);
        	}
        	if(channelnumC.equals("Red")) {
        		ChannelThree = projectedImages.get(2);
        	}
        	if(channelnumC.equals("FarRed")) {
        		ChannelFour = projectedImages.get(2);
        	}
        }
        
        if (channelnumD!="NotUsed") {
        	if(channelnumD.equals("Blue")) {
        		ChannelOne = projectedImages.get(3);
        	}
        	if(channelnumD.equals("Green")) {
        		ChannelTwo = projectedImages.get(3);
        	}
        	if(channelnumD.equals("Red")) {
        		ChannelThree = projectedImages.get(3);
        	}
        	if(channelnumD.equals("FarRed")) {
        		ChannelFour = projectedImages.get(3);
        	}
        }
     
     //   firstRun =true;
        labelImage = ImageJFunctions.wrap((RandomAccessibleInterval<T>) ChannelTwo,"Labelled Green Image");
		labelImage.show();
		IJ.run(labelImage, "Enhance Contrast", "saturated=0.35");
		
		RoiManager roiManager = null;
	//	roiManager.deselect(null);
	//	roiManager.reset();
        RandomAccessibleInterval<T> nucMask = FindNuclei(ChannelOne);
        roiManager=RoiManager.getRoiManager();
        Roi[] outlines = roiManager.getRoisAsArray();
     //rm = getRois(nucMask);
        
/*        ArrayList<Integer> roiNum = new ArrayList<Integer>();
        try {
			roiNum = selectRoi(ChannelOne,ChannelTwo,ChannelThree,ChannelFour);
		} catch (InterruptedException e) {
			
			e.printStackTrace();
		}
  */      
  //      MeasureSelectedRegions(roiNum,ChannelTwo,ChannelOne, ChannelThree, ChannelFour, rm);
        MeasureROIRegions(ChannelTwo,ChannelOne, ChannelThree, ChannelFour, outlines);
        ArrayList<Double> BkGrdValuesGreen = new ArrayList<Double>();
        ArrayList<Double> BkGrdValuesRed = new ArrayList<Double>();
        ArrayList<Double> BkGrdValuesFarRed = new ArrayList<Double>();
        
        if(ChannelTwo!=null) {
        	BkGrdValuesGreen = BkGrd(ChannelTwo);
        }
        if(ChannelThree!=null) {
       	 	BkGrdValuesRed = BkGrd(ChannelThree);
       }
       if(ChannelFour!=null) {
       	 	BkGrdValuesFarRed = BkGrd(ChannelFour);
       }
       String measurementType = "BkGrd";
      
       OutputResults(BkGrdValuesGreen,BkGrdValuesRed,BkGrdValuesFarRed, measurementType);
       
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
    	//Use Cellpose to separate and select the nuclei
    	uiService.show(ChannelOne);
    	ImagePlus imp = ImageJFunctions.wrap(ChannelOne,"Title");
    	imp.show();
    	Cellpose_Wrapper cpw = new Cellpose_Wrapper(modelpath.getPath(), envpath.getPath(), size, imp);
        cpw.run(true);
        
    //	 IJ.run("Cellpose Advanced", "diameter=" + 80 + " cellproba_threshold=" + 0.0 + " flow_threshold=" + 0.4
     //           + " anisotropy=" + 1.0 + " diam_threshold=" + 12.0 + " model=" + "nuclei" + " nuclei_channel=" + 0
      //          + " cyto_channel=" + 1 + " dimensionmode=" + "2D" + " stitch_threshold=" + -1 + " omni=" + false
       //         + " cluster=" + false + " additional_flags=" + "");
    	
    	Dataset image = ij.imageDisplay().getActiveDataset();
    	nucMask = (RandomAccessibleInterval<T>) image;
    	return nucMask;
    	
    }
    
    private RoiManager getRois(RandomAccessibleInterval<T> nucMask) {
    	RoiManager rm;
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
    
  /*  private ArrayList<Integer> selectRoi(RandomAccessibleInterval<T>ChannelOne, RandomAccessibleInterval<T>ChannelTwo, RandomAccessibleInterval<T>ChannelThree, RandomAccessibleInterval<T>ChannelFour) throws InterruptedException {
    	
    	
    	ImagePlus impTwo = null;
    	ImagePlus impThree = null;
    	ImagePlus impFour =null;
    	RoiManager roiManager = new RoiManager();
    	roiManager = RoiManager.getInstance();
        Roi[] outlines = roiManager.getRoisAsArray();
        ArrayList<Integer> roiNum = new ArrayList<Integer>();
        ImagePlus imp = ImageJFunctions.wrap(ChannelOne,"");
        imp.show();
        IJ.run(imp, "Enhance Contrast", "saturated=0.35");  
        IJ.run("Out [-]", "");
        if (ChannelTwo!=null) {
        	impTwo = ImageJFunctions.wrap(ChannelTwo,"");
        	impTwo.show();
        	IJ.run(impTwo, "Enhance Contrast", "saturated=0.35"); 
        	IJ.run("Out [-]", "");
        }
        if (ChannelThree!=null) {
        	impThree = ImageJFunctions.wrap(ChannelThree,"");
        	impThree.show();
        	IJ.run(impThree, "Enhance Contrast", "saturated=0.35"); 
        	IJ.run("Out [-]", "");
        }
        if (ChannelFour!=null) {
        	impFour = ImageJFunctions.wrap(ChannelThree,"");
        	impFour.show();
        	IJ.run(impFour, "Enhance Contrast", "saturated=0.35"); 
        	IJ.run("Out [-]", "");
        }
        new WaitForUserDialog("Arrange Images", "Place the images so that you can see all channels").show();
        
        String answer = "y";
        do {
        	imp.getCanvas().addMouseListener(mouseListener);
            double scale = imp.getCanvas().getMagnification();
        	new WaitForUserDialog("Click", "Click an object (ONLY CLICK OBJECT IMAGE) then OK").show();
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
		if (impFour!=null) {
			impFour.changes=false;
			impFour.close();
		}
 		return roiNum;
        
    }
    */
    @SuppressWarnings("unchecked")
//	private void  MeasureSelectedRegions(ArrayList<Integer>roiNum, RandomAccessibleInterval<T> ChannelTwo, RandomAccessibleInterval<T> ChannelOne, RandomAccessibleInterval<T>ChannelThree, RandomAccessibleInterval<T> ChannelFour, RoiManager rm) {
    	
    private void  MeasureROIRegions(RandomAccessibleInterval<T> ChannelTwo, RandomAccessibleInterval<T> ChannelOne, RandomAccessibleInterval<T>ChannelThree, RandomAccessibleInterval<T> ChannelFour, Roi[] outlines) {
    	
    	ImagePlus greenChannel = ImageJFunctions.wrap(ChannelTwo,"Green");
		greenChannel.show();
		IJ.run(greenChannel, "Enhance Contrast", "saturated=0.35");
		ImagePlus redChannel = null;
		ImagePlus FarRedChannel = null;
		
		if(ChannelThree!=null) {
			redChannel = ImageJFunctions.wrap(ChannelThree,"Red");
			redChannel.show();
			IJ.run(redChannel, "Enhance Contrast", "saturated=0.35");
		}
		if(ChannelFour!=null) {
			FarRedChannel = ImageJFunctions.wrap(ChannelFour,"FarRed");
			FarRedChannel.show();
			IJ.run(FarRedChannel, "Enhance Contrast", "saturated=0.35");
		}
		
		double minThresh = 0.0;
    	for(int a=0;a<outlines.length;a++) {
    		Roi nucleus = outlines[a];
    		greenChannel.setRoi(nucleus);
			minThresh = (nucleus.getStatistics().stdDev*10) + nucleus.getStatistics().mean;
    		MeasureAnyGreenAndRed(minThresh,greenChannel,redChannel,FarRedChannel);
    	}
    	
    	//  RealMask mask = null;
    	
    /*	
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
    		cellNum = x;
        	CheckForSpots(thegfpResultingPixels, ChannelTwo, ChannelThree, ChannelFour, cellNum);
        	
    	}
    	*/
    }
    
    private void MeasureAnyGreenAndRed(double minThresh, ImagePlus greenChannel, ImagePlus redChannel, ImagePlus FarRedChannel) {
    	
    }
    
    private void CheckForSpots(IterableInterval<T> thegfpResultingPixels, RandomAccessibleInterval<T> ChannelTwo, RandomAccessibleInterval<T> ChannelThree, RandomAccessibleInterval<T> ChannelFour, int cellNum) {
    	
   
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
		
		//Get position of Spot so that image can be labelled for later
		int xPos = (int) Positions[0]-(activeMaxSpotSize/3);
		int yPos = (int) Positions[1]-(activeMaxSpotSize/3);
		
		ArrayList<Double>valuesG = new ArrayList<Double>();
		ArrayList<Double>valuesR = new ArrayList<Double>();
		ArrayList<Double>valuesFR = new ArrayList<Double>();
		
		if (ChannelTwo!=null) {
			valuesG = measureSpots(ChannelTwo, Positions);
		}
		if (ChannelThree!=null) {
			valuesR = measureSpots(ChannelThree, Positions);
		}
		if (ChannelFour!=null) {
			valuesFR = measureSpots(ChannelFour, Positions);
		}

		String measurementType = "test";
	
		OutputResults(valuesG,valuesR,valuesFR,measurementType);
		
		
		//Label the Green Image
    	ImageProcessor ip = labelImage.getProcessor();
		Font font = new Font("SansSerif", Font.PLAIN, 18);
		ip.setFont(font);
		ip.setColor(Color.getHSBColor(0, 0, 255));
    	String cellNumber = Integer.toString(cellNum+1);
    	ip.drawString(cellNumber, xPos+12, yPos+12);
    	
		labelImage.updateAndDraw();
		labelImage.setRoi(new OvalRoi(xPos, yPos,activeMaxSpotSize,activeMaxSpotSize));
		IJ.run(labelImage, "Draw", "slice");
		IJ.run(labelImage, "Select None", "");
		
		
    }
    
 
    public ArrayList<Double> measureSpots(RandomAccessibleInterval<T> Channel, double [] Positions) {
		ArrayList<Double> spotValues = new ArrayList<Double>();
    	
		ClearResults();
		ImagePlus imp = ImageJFunctions.wrap(Channel,"TestChannel");
		imp.show();
		IJ.run(imp, "Enhance Contrast", "saturated=0.35");
		int xPos = (int) Positions[0]-(activeMaxSpotSize/3);
		int yPos = (int) Positions[1]-(activeMaxSpotSize/3);
		imp.setRoi(new OvalRoi(xPos, yPos,activeMaxSpotSize,activeMaxSpotSize));
		IJ.run("Set Measurements...", "area mean standard modal min centroid median redirect=None decimal=2");
		IJ.run(imp, "Measure", "");
		ResultsTable rt = new ResultsTable();	
		rt = Analyzer.getResultsTable();
		spotValues.add(rt.getValueAsDouble(1, 0));
		spotValues.add(rt.getValueAsDouble(3, 0));
		spotValues.add(rt.getValueAsDouble(4, 0));
		spotValues.add(rt.getValueAsDouble(5, 0));
		spotValues.add(rt.getValueAsDouble(21, 0));
		
		ClearResults();
		
		imp.changes=false;
		imp.close();
		
    	return spotValues;
    }
  
    public ArrayList<Double> BkGrd(RandomAccessibleInterval<T>Channel){
    	ArrayList<Double> BkGrdValues = new ArrayList<Double>();
    	ImagePlus BkGrdImp = ImageJFunctions.wrap((RandomAccessibleInterval<T>) Channel,"Bkground Image");
    	ClearResults();
    	BkGrdImp.show();
    	IJ.run(BkGrdImp, "Enhance Contrast", "saturated=0.35");
    	 new WaitForUserDialog("Background", "Draw region for background").show();
    	 IJ.run(BkGrdImp, "Measure", "");
 		 ResultsTable rtG = Analyzer.getResultsTable();
 		 BkGrdValues.add(rtG.getValueAsDouble(1, 0)); // Mean Background
 		 BkGrdValues.add(rtG.getValueAsDouble(3, 0)); // Mode Background
 		 BkGrdValues.add(rtG.getValueAsDouble(4, 0)); // Min Background
 		 BkGrdValues.add(rtG.getValueAsDouble(5, 0)); // Max Background
 		 BkGrdValues.add(rtG.getValueAsDouble(21, 0)); // Median Background
 		 
 		ClearResults();
 		BkGrdImp.changes=false;
 		BkGrdImp.close();
 	
 		return BkGrdValues;	 
 		
    }
    
    public void OutputResults(ArrayList<Double>ValG, ArrayList<Double>ValR, ArrayList<Double>ValFR,String measurementType) {
        
    	String name = file.getAbsolutePath();
		String newName = name.substring(0, name.indexOf("."));	
		String CreateName = newName + ".txt";
		String FILE_NAME = CreateName;
		
		try{
			FileWriter fileWriter = new FileWriter(FILE_NAME,true);
			BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
	
			if(cellNum==0 && firstRun==true) {
				bufferedWriter.newLine();
				bufferedWriter.write(" File= " + name);
				bufferedWriter.newLine();
				firstRun = false;
			}		
			if(ValG.size()>0 && measurementType.equals("test")) {
				bufferedWriter.write("Cell " + (cellNum+1) + " Green Mean = " + ValG.get(0) + " Green Mode = "  + ValG.get(1) + " Green Min = " + ValG.get(2) + " Green Max = " + ValG.get(3) + " Green Median = " + ValG.get(4));		
				bufferedWriter.newLine();
			}
			if(ValR.size()>0 && measurementType.equals("test")) {
				bufferedWriter.write("Cell " + (cellNum+1) + " Red Mean = " + ValR.get(0) + " Red Mode = " + ValR.get(1) + " Red Min = " + ValR.get(2) + " Red Max = " + ValR.get(3) + " Red Median = " + ValR.get(4));		
				bufferedWriter.newLine();
			}
			if(ValFR.size()>0 && measurementType.equals("test")) {
				bufferedWriter.write("Cell " + (cellNum+1) + " FarRed Mean = " + ValFR.get(0) + " FarRed Mode = " + ValFR.get(1) + " FarRed Min = " + ValFR.get(2) + " FarRed Max = " + ValFR.get(3) + " FarRed Median = " + ValFR.get(4));		
				bufferedWriter.newLine();
			}
			
			if(ValG.size()>0 && measurementType.equals("BkGrd")) {
				bufferedWriter.write("BkGrd Green Mean = " + ValG.get(0) + " BkGrd Green Mode = "  + ValG.get(1) + " BkGrd Green Min = " + ValG.get(2) + " BkGrd Green Max = " + ValG.get(3) + " BkGrd Green Median = " + ValG.get(4));		
				bufferedWriter.newLine();
			}
			if(ValR.size()>0 && measurementType.equals("BkGrd")) {
				bufferedWriter.write("BkGrd Red Mean = " + ValR.get(0) + " BkGrd Red Mode = " + ValR.get(1) + " BkGrd Red Min = " + ValR.get(2) + " BkGrd Red Max = " + ValR.get(3) + " BkGrd Red Median = " + ValR.get(4));		
				bufferedWriter.newLine();
			}
			if(ValFR.size()>0 && measurementType.equals("BkGrd")) {
				bufferedWriter.write("BkGrd FarRed Mean = " + ValFR.get(0) + " BkGrd FarRed Mode = " + ValFR.get(1) + " BkGrd FarRed Min = " + ValFR.get(2) + " BkGrd FarRed Max = " + ValFR.get(3) + " BkGrd FarRed Median = " + ValFR.get(4));		
				bufferedWriter.newLine();
			}
			bufferedWriter.close();

		}
		catch(IOException ex) {
			System.out.println(
            "Error writing to file '"
            + FILE_NAME + "'");
		}
		
    }
  
 /*   public MouseListener mouseListener = new MouseListener() {
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
   */ 
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
