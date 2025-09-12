/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 *     
 */

package bio.coil.CoilEdinburgh;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.display.ImageDisplayService;
import net.imagej.display.WindowService;
import net.imagej.ops.Op;
import net.imagej.ops.OpService;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;

import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.display.DisplayService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import ij.IJ;

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

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

		RoiManager roiManager = null;
		FindNuclei(ChannelOne);
		
        roiManager=RoiManager.getRoiManager();
        Roi[] outlines = roiManager.getRoisAsArray();
  
        MeasureROIRegions(ChannelTwo,ChannelOne, ChannelThree, ChannelFour, outlines);  

        IJ.run("Close All", "");
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
    
    public void  FindNuclei(RandomAccessibleInterval<T>ChannelOne){
    			
    	//Use Cellpose to separate and select the nuclei
    	uiService.show(ChannelOne);
    	ImagePlus imp = ImageJFunctions.wrap(ChannelOne,"Title");
    	imp.show();
    	Cellpose_Wrapper cpw = new Cellpose_Wrapper(modelpath.getPath(), envpath.getPath(), size, imp);
        cpw.run(true);
        ImagePlus showImp = WindowManager.getCurrentImage();
        ImagePlus squashedImp = showImp.flatten();
        String name = file.getAbsolutePath();
		String newName = name.substring(0, name.indexOf("."));	
		String imageName = newName + ".jpg";
        IJ.saveAs(squashedImp, "Jpeg", imageName);
    	
    }

    private void  MeasureROIRegions(RandomAccessibleInterval<T> ChannelTwo, RandomAccessibleInterval<T> ChannelOne, RandomAccessibleInterval<T>ChannelThree, RandomAccessibleInterval<T> ChannelFour, Roi[] outlines) {
    	
    	int greenChannelID=0;
		int redChannelID=0;
		
    	ImagePlus greenChannel = ImageJFunctions.wrap(ChannelTwo,"Green");
		greenChannel.show();
		greenChannelID = greenChannel.getID();
		IJ.run(greenChannel, "Enhance Contrast", "saturated=0.35");
		ImagePlus redChannel = null;
		ImagePlus FarRedChannel = null;
		
		
		if(ChannelThree!=null) {
			redChannel = ImageJFunctions.wrap(ChannelThree,"Red");
			redChannel.show();
			redChannelID=redChannel.getID();
			IJ.run(redChannel, "Enhance Contrast", "saturated=0.35");
		}
		
		double minThresh = 0.0;
    	for(int a=0;a<outlines.length;a++) {
    		Roi nucleus = outlines[a];
    		greenChannel.setRoi(nucleus);
    		//CALCULATION THE STANDARD DEVIATION TO RECOGNISE A SPOT
			minThresh = (nucleus.getStatistics().stdDev*10) + nucleus.getStatistics().mean;
    		MeasureAnyGreenAndRed(minThresh,greenChannel,redChannel,FarRedChannel,nucleus,a);
   
    	}
    	
    	 double [] BkGrdValuesGreen = new double [4];
    	 double [] BkGrdValuesRed = new double [4];
    	
    	    
    	if(ChannelTwo!=null) {
    		IJ.selectWindow(greenChannelID);
    		BkGrdValuesGreen = BkGrd(greenChannel);
        }
    
        if(ChannelThree!=null) {
        	IJ.selectWindow(redChannelID);
        	BkGrdValuesRed = BkGrd(redChannel);
       }
    
       
       double[][] theBkGrdResults = new double[4][4];
       
       theBkGrdResults[0]=BkGrdValuesGreen;
       
       theBkGrdResults[1]=BkGrdValuesRed;
       
       int a=-1;
   	   OutputResults(theBkGrdResults,a);
    	
    }
    
    private void MeasureAnyGreenAndRed(double minThresh, ImagePlus greenChannel, ImagePlus redChannel, ImagePlus FarRedChannel,Roi nucleus, int a) {
    	int greenChannelID = greenChannel.getID();
    	
    	int upperThreshold = greenChannel.getBitDepth();
    	double maxThresh = 0;
    	if(upperThreshold == 12) {
    		maxThresh = 4095;
    	}else {
    		maxThresh = 65535;
    	}
    	
    	IJ.selectWindow(greenChannelID);
    	greenChannel.setRoi(nucleus);
    	RoiManager roiManager;
    	if (nucleus.getStatistics().max >= minThresh) {
    		IJ.setThreshold(minThresh, maxThresh);
    		IJ.run(greenChannel, "Analyze Particles...", "size=4-Infinity display clear add");
    		roiManager=RoiManager.getRoiManager();
            Roi[] smallOutlines = roiManager.getRoisAsArray();
            IJ.run(greenChannel, "Select None", "");
            double[][] theResults = GetResults(nucleus, smallOutlines, greenChannel, redChannel);     
            OutputResults(theResults,a);
    	}
    	 
    }
    
    private double[][] GetResults(Roi nucleus, Roi[] smallOutlines, ImagePlus greenChannel, ImagePlus redChannel) {
    	int redChannelID = redChannel.getID();
    	int greenChannelID = greenChannel.getID();
    	double [][] theResults = new double[4][4];
    	
    	//Get Green Channel Results for the whole cell
    	IJ.selectWindow(greenChannelID);
    	double [] theGreenCellResults = new double[4];
    	double [] theGreenDotResults = new double[4];
    	greenChannel.setRoi(nucleus);
    	theGreenCellResults[0] = nucleus.getStatistics().area;
    	theGreenCellResults[1] = greenChannel.getProcessor().getStatistics().min;
    	theGreenCellResults[2] = greenChannel.getProcessor().getStatistics().max;
    	theGreenCellResults[3] = greenChannel.getProcessor().getStatistics().mean;
    	IJ.run(greenChannel, "Select None", "");
    	//Get Results for largest of the green dots
    	double largestArea = 0;
    	for(int x=0; x<smallOutlines.length;x++) {
    		Roi small = smallOutlines[x];
    		greenChannel.setRoi(small);
    		if(small.getStatistics().area > largestArea) {
    			theGreenDotResults[0]= small.getStatistics().area;
    			theGreenDotResults[1]= greenChannel.getProcessor().getStatistics().min;
    			theGreenDotResults[2]= greenChannel.getProcessor().getStatistics().max;
    			theGreenDotResults[3]= greenChannel.getProcessor().getStatistics().mean;
    			largestArea = small.getStatistics().area;
    			
    		}
    	}
    	IJ.run(greenChannel, "Select None", "");
    	
    	//Get Red Channel Results for the whole cell
    	IJ.selectWindow(redChannelID);
    	double [] theRedCellResults = new double[4];
    	double [] theRedDotResults = new double[4];
    	redChannel.setRoi(nucleus);
    	
    	theRedCellResults[0] = nucleus.getStatistics().area;
    	theRedCellResults[1] = redChannel.getProcessor().getStatistics().min;
    	theRedCellResults[2] = redChannel.getProcessor().getStatistics().max;
    	theRedCellResults[3] = redChannel.getProcessor().getStatistics().mean;
    	
    	//Get Results for largest of the red dots
    	largestArea = 0;
    	for(int x=0; x<smallOutlines.length;x++) {
    		Roi smallRed = smallOutlines[x];
    		
    		if(smallRed.getStatistics().area > largestArea) {
    			redChannel.setRoi(smallRed);
    			theRedDotResults[0]= smallRed.getStatistics().area;
    			theRedDotResults[1]= redChannel.getProcessor().getStatistics().min;
    			theRedDotResults[2]= redChannel.getProcessor().getStatistics().max;
    			theRedDotResults[3]= redChannel.getProcessor().getStatistics().mean;
    			largestArea = smallRed.getStatistics().area;
    		}
    	}
    	
    	theResults[0] = theGreenCellResults;
    	theResults[1] = theGreenDotResults;
    	theResults[2] = theRedCellResults;
    	theResults[3] = theRedDotResults;
    	return theResults;
    }
    
    public void OutputResults(double [][] theResults, int a) {
    	String name = file.getAbsolutePath();
		String newName = name.substring(0, name.indexOf("."));	
		String CreateName = newName + ".txt";
		String FILE_NAME = CreateName;
		
		if(a>0) {
		try{
			FileWriter fileWriter = new FileWriter(FILE_NAME,true);
			BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
			bufferedWriter.newLine();
			bufferedWriter.write(" File= " + name + "," + " Cell Number " + "," + (a+1) + "," +" Green Whole Cell Area " + "," + theResults[0][0] + "," + " Green Whole Cell Minimum " + "," + theResults[0][1] + "," + " Green Whole Cell Maximum " + "," + theResults[0][2] + "," + " Green Whole Cell Mean " + "," + theResults[0][3]);
			bufferedWriter.newLine();
			bufferedWriter.write(" File= " + name + "," + " Cell Number " + "," + (a+1) + "," +" Green ROI Area " + "," + theResults[1][0] + "," + " Green ROI Minimum " + "," + theResults[1][1] + "," + " Green ROI Maximum " + "," + theResults[1][2] + "," + " Green ROI Mean " + "," + theResults[1][3]);
			bufferedWriter.newLine();
			bufferedWriter.write(" File= " + name + "," + " Cell Number " + "," + (a+1) + "," +" Red Whole Cell Area " + "," + theResults[2][0] + "," + " Red Whole Cell Minimum " + "," + theResults[2][1] + "," + " Red Whole Cell Maximum " + "," + theResults[2][2] + "," + " Red Whole Cell Mean " + "," + theResults[2][3]);	
			bufferedWriter.newLine();
			bufferedWriter.write(" File= " + name + "," + " Cell Number " + "," + (a+1) + "," +" Red ROI Area " + "," + theResults[3][0] + "," + " Red ROI Minimum " + "," + theResults[3][1] + "," + " Red ROI Maximum " + "," + theResults[3][2] + "," + " Red ROI Mean " + "," + theResults[3][3]);
			
			bufferedWriter.close();
		}
			catch(IOException ex) {
				System.out.println(
	            "Error writing to file '"
	            + FILE_NAME + "'");
			}
		}else {
			FileWriter fileWriter;
			try {
				fileWriter = new FileWriter(FILE_NAME,true);
				BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
				bufferedWriter.newLine();
				bufferedWriter.write(" File= " + name + "," + "," +" Green Bkgrd Area ROI" + "," + theResults[0][0] + "," + " Green BkGrd Minimum " + "," + theResults[0][1] + "," + " Green BkGrd Maximum " + "," + theResults[0][2] + "," + " Green BkGrd Mean " + "," + theResults[0][3]);
				bufferedWriter.newLine();
				bufferedWriter.write(" File= " + name + "," + "," +" Red Bkgrd Area ROI" + "," + theResults[1][0] + "," + " Red BkGrd Minimum " + "," + theResults[1][1] + "," + " Red BkGrd Maximum " + "," + theResults[1][2] + "," + " Red BkGrd Mean " + "," + theResults[1][3]);
				bufferedWriter.newLine();
				
				bufferedWriter.close();
			} catch (IOException e) {	
				e.printStackTrace();
			}
			
		}
		
		
		
    }
 
    private double[] BkGrd(ImagePlus BkGrdImp){
    	double [] BkGrdValues = new double[4];
 
    	 new WaitForUserDialog("Background", "Draw region for background").show();
 
    	 BkGrdValues[0]= BkGrdImp.getStatistics().area;
    	 BkGrdValues[1]= BkGrdImp.getProcessor().getStatistics().min;
    	 BkGrdValues[2]= BkGrdImp.getProcessor().getStatistics().max;
    	 BkGrdValues[3]= BkGrdImp.getProcessor().getStatistics().mean;
 	
 		return BkGrdValues;	 
 		
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
